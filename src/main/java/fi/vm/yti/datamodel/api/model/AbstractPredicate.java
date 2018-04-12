package fi.vm.yti.datamodel.api.model;

import fi.vm.yti.datamodel.api.service.GraphManager;
import fi.vm.yti.datamodel.api.utils.LDHelper;
import org.apache.jena.iri.IRI;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

public class AbstractPredicate extends AbstractResource {

    private String provUUID;
    private static final Logger logger = LoggerFactory.getLogger(AbstractPredicate.class.getName());


    public AbstractPredicate(GraphManager graphManager) {
        super(graphManager);
    }

    public AbstractPredicate(IRI graphIRI,
                             GraphManager graphManager) {
       super(graphIRI, graphManager);
    }

    public AbstractPredicate(Model graph,
                             GraphManager graphManager) {
        super(graphManager);

        this.graph = graph;

        try {

                ResIterator subjects = asGraph().listSubjectsWithProperty(RDF.type);

                if(!subjects.hasNext()) {
                    throw new IllegalArgumentException("Expected at least 1 typed resource");
                }

                Resource predicateResource = null;

                while(subjects.hasNext()) {
                    Resource res = subjects.next();
                    if(res.hasProperty(RDF.type, OWL.ObjectProperty) || res.hasProperty(RDF.type, OWL.DatatypeProperty)) {
                        if(predicateResource!=null) {
                            throw new IllegalArgumentException("Multiple class resources");
                        } else {
                            predicateResource = res;
                        }
                    }
                }

                if(predicateResource==null) {
                    throw new IllegalArgumentException("Expected rdfs:Class or sh:Shape");
                }

            Statement isDefinedBy = predicateResource.getRequiredProperty(RDFS.isDefinedBy);
            Resource modelResource = isDefinedBy.getObject().asResource();

            this.dataModel = new DataModel(LDHelper.toIRI(modelResource.toString()), graphManager);
            this.id = LDHelper.toIRI(predicateResource.toString());
            this.provUUID = "urn:uuid:"+UUID.randomUUID().toString();

            predicateResource.removeAll(DCTerms.identifier);
            predicateResource.addProperty(DCTerms.identifier,ResourceFactory.createPlainLiteral(provUUID));

        } catch(Exception ex)  {
            logger.warn(ex.getMessage());
            throw new IllegalArgumentException("Expected 1 predicate (isDefinedBy)");

        }

        if(!getId().startsWith(getModelId())) {
            throw new IllegalArgumentException("Predicate ID should start with model ID!");
        }


    }

    public String getProvUUID() { return this.provUUID; }
    public List<UUID> getOrganizations() { return this.dataModel.getOrganizations(); }

}
