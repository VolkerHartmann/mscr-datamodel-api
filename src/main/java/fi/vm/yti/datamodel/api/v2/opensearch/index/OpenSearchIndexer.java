package fi.vm.yti.datamodel.api.v2.opensearch.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.vm.yti.datamodel.api.index.OpenSearchConnector;
import fi.vm.yti.datamodel.api.v2.dto.Iow;
import fi.vm.yti.datamodel.api.v2.dto.ModelConstants;
import fi.vm.yti.datamodel.api.v2.mapper.ClassMapper;
import fi.vm.yti.datamodel.api.v2.mapper.ModelMapper;
import fi.vm.yti.datamodel.api.v2.service.JenaService;
import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.*;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;


@Service
public class OpenSearchIndexer {

    public static final String OPEN_SEARCH_INDEX_MODEL = "models_v2";
    private static final String OPEN_SEARCH_INDEX_CLASS = "class_v2";

    private final Logger logger = LoggerFactory.getLogger(OpenSearchIndexer.class);
    private static final String GRAPH_VARIABLE = "?model";
    private final OpenSearchConnector openSearchConnector;
    private final ObjectMapper objectMapper;
    private final JenaService jenaService;
    private final ModelMapper modelMapper;
    private final ClassMapper classMapper;

    private final RestHighLevelClient esClient;


    public OpenSearchIndexer(OpenSearchConnector openSearchConnector,
                             ObjectMapper objectMapper,
                             JenaService jenaService,
                             ModelMapper modelMapper,
                             ClassMapper classMapper, RestHighLevelClient esClient){
        this.openSearchConnector = openSearchConnector;
        this.objectMapper = objectMapper;
        this.jenaService = jenaService;
        this.modelMapper = modelMapper;
        this.classMapper = classMapper;
        this.esClient = esClient;
    }

    public void reindex() {
        try {
            openSearchConnector.cleanIndex(OPEN_SEARCH_INDEX_MODEL);
            openSearchConnector.cleanIndex(OPEN_SEARCH_INDEX_CLASS);
            logger.info("v2 Indexes cleaned");
            openSearchConnector.createIndex(OPEN_SEARCH_INDEX_MODEL, getModelMappings());
            openSearchConnector.createIndex(OPEN_SEARCH_INDEX_CLASS, getClassMappings());

            initSearchIndexes();
            logger.info("Indexes initialized");
        } catch (IOException ex) {
            logger.warn("Reindex failed!", ex);
        }
    }

    private String getModelMappings() throws IOException {
        InputStream is = OpenSearchIndexer.class.getClassLoader().getResourceAsStream("model_v2_mapping.json");
        Object obj = objectMapper.readTree(is);
        return objectMapper.writeValueAsString(obj);
    }

    private String getClassMappings() throws IOException {
        InputStream is = OpenSearchIndexer.class.getClassLoader().getResourceAsStream("class_v2_mapping.json");
        Object obj = objectMapper.readTree(is);
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * A new model to index
     * @param model Model to index
     */
    public void createModelToIndex(IndexModel model){
        logger.info("Indexing: {}", model.getId());
        openSearchConnector.putToIndex(OPEN_SEARCH_INDEX_MODEL, model.getId(), model);
    }

    /**
     * Update existing model in index
     * @param model Model to index
     */
    public void updateModelToIndex(IndexModel model){
        openSearchConnector.updateToIndex(OPEN_SEARCH_INDEX_MODEL, model.getId(), model);
    }

    /**
     * A new class to index
     * @param indexClass Class to index
     */
    public void createClassToIndex(IndexClass indexClass){
        logger.info("Indexing: {}", indexClass.getId());
        openSearchConnector.putToIndex(OPEN_SEARCH_INDEX_CLASS, indexClass.getId(), indexClass);
    }

    /**
     * Update exisitng class in index
     * @param indexClass Class to index
     */
    public void updateClassToIndex(IndexClass indexClass){
        logger.info("Updating index for: {}", indexClass.getId());
        openSearchConnector.updateToIndex(OPEN_SEARCH_INDEX_CLASS, indexClass.getId(), indexClass);
    }


    /**
     * Init search indexes
     */
    private void initSearchIndexes() throws IOException {
        initModelIndex();
        initClassIndex();
    }

    /**
     * Init model index
     */
    public void initModelIndex() throws IOException {
        var constructBuilder = new ConstructBuilder()
                .addPrefixes(ModelConstants.PREFIXES);
        addProperty(constructBuilder, RDFS.label, "?prefLabel");
        addOptional(constructBuilder, RDFS.comment, "?comment");
        addProperty(constructBuilder, RDF.type, "?modelType");
        addProperty(constructBuilder, OWL.versionInfo, "?versionInfo");
        addProperty(constructBuilder, DCTerms.modified, "?modified");
        addProperty(constructBuilder, DCTerms.created, "?created");
        addProperty(constructBuilder, DCTerms.contributor, "?contributor");
        addProperty(constructBuilder, DCTerms.isPartOf, "?isPartOf");
        addOptional(constructBuilder, Iow.contentModified, "?contentModified");
        addOptional(constructBuilder, Iow.documentation, "?documentation");
        //TODO swap to commented text once older migration is ready
        //addProperty(constructBuilder, DCTerms.language, "?language");
        constructBuilder.addConstruct(GRAPH_VARIABLE, DCTerms.language, "?language")
                .addWhereValueVar("?langTypes", DCTerms.language, "dcterms:language/rdf:rest*/rdf:first")
                .addWhere(GRAPH_VARIABLE, "?langTypes", "?language");

        var indexModels = jenaService.constructWithQuery(constructBuilder.build());
        var it = indexModels.listSubjects();
        var list = new ArrayList<IndexModel>();
        while(it.hasNext()){
            var resource = it.next();
            var newModel = ModelFactory.createDefaultModel()
                    .add(resource.listProperties());
            var indexModel = modelMapper.mapToIndexModel(resource.getLocalName(), newModel);
            list.add(indexModel);
        }
        var values = objectMapper.valueToTree(list);
        bulkInsert(OPEN_SEARCH_INDEX_MODEL, values);
    }

    public void initClassIndex() throws IOException {
        var constructBuilder = new ConstructBuilder()
                .addPrefixes(ModelConstants.PREFIXES)
                .addWhere(GRAPH_VARIABLE, RDF.type, "?classType")
                .addWhereValueVar("?classType", OWL.Class);
        addProperty(constructBuilder, RDFS.label, "?label");
        addProperty(constructBuilder, OWL.versionInfo, "?versionInfo");
        addProperty(constructBuilder, DCTerms.modified, "?modified");
        addProperty(constructBuilder, DCTerms.created, "?created");
        addOptional(constructBuilder, Iow.contentModified, "?contentModified");
        addProperty(constructBuilder, RDFS.isDefinedBy, "?isDefinedBy");
        addOptional(constructBuilder, SKOS.note, "?comment");
        addOptional(constructBuilder, RDFS.subClassOf, "?subClassOf");
        addOptional(constructBuilder, OWL.equivalentClass, "?equivalentClass");
        var indexClasses = jenaService.constructWithQuery(constructBuilder.build());
        var it = indexClasses.listSubjects();
        var list = new ArrayList<IndexClass>();
        while(it.hasNext()){
            var resource = it.next();
            var newClass = ModelFactory.createDefaultModel()
                    .add(resource.listProperties());
            var indexClass = classMapper.mapToIndexClass(newClass, resource.getURI());
            list.add(indexClass);
        }
        var values = objectMapper.valueToTree(list);
        bulkInsert(OPEN_SEARCH_INDEX_CLASS, values);
    }

    private void addProperty(ConstructBuilder builder, Property property, String propertyName){
        builder.addConstruct(GRAPH_VARIABLE, property, propertyName)
                .addWhere(GRAPH_VARIABLE, property, propertyName);
    }

    private void addOptional(ConstructBuilder builder, Property property, String propertyName){
        builder.addConstruct(GRAPH_VARIABLE, property, propertyName)
                .addOptional(GRAPH_VARIABLE, property, propertyName);
    }

    public void bulkInsert(String indexName, JsonNode indexObjects) throws IOException {
        var bulkrequest = new BulkRequest();
        indexObjects.forEach(indexObject -> {
            var req = new IndexRequest(indexName)
                    .id(indexObject.get("id").asText())
                    .source(objectMapper.convertValue(indexObject, Map.class));
            bulkrequest.add(req);
        });
        if(bulkrequest.numberOfActions() > 0){
            var res = esClient.bulk(bulkrequest, RequestOptions.DEFAULT);
            logger.debug("Bulk insert status: {}", res.status().getStatus());
        }

    }
}
