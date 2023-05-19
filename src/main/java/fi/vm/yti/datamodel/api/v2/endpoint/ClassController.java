package fi.vm.yti.datamodel.api.v2.endpoint;

import fi.vm.yti.datamodel.api.security.AuthorizationManager;
import fi.vm.yti.datamodel.api.v2.dto.*;
import fi.vm.yti.datamodel.api.v2.endpoint.error.MappingError;
import fi.vm.yti.datamodel.api.v2.endpoint.error.ResourceNotFoundException;
import fi.vm.yti.datamodel.api.v2.mapper.ClassMapper;
import fi.vm.yti.datamodel.api.v2.mapper.MapperUtils;
import fi.vm.yti.datamodel.api.v2.mapper.ResourceMapper;
import fi.vm.yti.datamodel.api.v2.opensearch.dto.ResourceSearchRequest;
import fi.vm.yti.datamodel.api.v2.opensearch.index.IndexResource;
import fi.vm.yti.datamodel.api.v2.opensearch.index.OpenSearchIndexer;
import fi.vm.yti.datamodel.api.v2.service.GroupManagementService;
import fi.vm.yti.datamodel.api.v2.service.JenaService;
import fi.vm.yti.datamodel.api.v2.service.SearchIndexService;
import fi.vm.yti.datamodel.api.v2.service.TerminologyService;
import fi.vm.yti.datamodel.api.v2.validator.ValidClass;
import fi.vm.yti.security.AuthenticatedUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.jena.graph.NodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static fi.vm.yti.security.AuthorizationException.check;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("v2/class")
@Tag(name = "Class" )
@Validated
public class ClassController {

    private final Logger logger = LoggerFactory.getLogger(ClassController.class);

    private final AuthorizationManager authorizationManager;
    private final JenaService jenaService;
    private final OpenSearchIndexer openSearchIndexer;
    private final GroupManagementService groupManagementService;
    private final AuthenticatedUserProvider userProvider;
    private final TerminologyService terminologyService;
    private final SearchIndexService searchIndexService;

    public ClassController(AuthorizationManager authorizationManager,
                           JenaService jenaService,
                           OpenSearchIndexer openSearchIndexer,
                           GroupManagementService groupManagementService,
                           AuthenticatedUserProvider userProvider,
                           TerminologyService terminologyService,
                           SearchIndexService searchIndexService){
        this.authorizationManager = authorizationManager;
        this.jenaService = jenaService;
        this.openSearchIndexer = openSearchIndexer;
        this.groupManagementService = groupManagementService;
        this.userProvider = userProvider;
        this.terminologyService = terminologyService;
        this.searchIndexService = searchIndexService;
    }

    @Operation(summary = "Add a class to a model")
    @ApiResponse(responseCode = "200", description = "Class added to model successfully")
    @PutMapping(value = "/{prefix}", consumes = APPLICATION_JSON_VALUE)
    public void createClass(@PathVariable String prefix, @RequestBody @ValidClass ClassDTO classDTO){
        var modelURI = ModelConstants.SUOMI_FI_NAMESPACE + prefix;
        if(jenaService.doesResourceExistInGraph(modelURI, modelURI + "#" + classDTO.getIdentifier())){
            throw new MappingError("Class already exists");
        }
        var model = jenaService.getDataModel(modelURI);
        if(model == null){
            throw new ResourceNotFoundException(modelURI);
        }
        check(authorizationManager.hasRightToModel(prefix, model));

        terminologyService.resolveConcept(classDTO.getSubject());

        var indexedResources = new ArrayList<String>();
        var classURI = ClassMapper.createClassAndMapToModel(modelURI, model, classDTO, userProvider.getUser());
        indexedResources.add(classURI);

        if (MapperUtils.isApplicationProfile(model.getResource(modelURI))) {
            var propertiesModel = jenaService.findResources(classDTO.getProperties());
            var properties = ClassMapper.mapPlaceholderPropertyShapes(model, classURI, propertiesModel, userProvider.getUser());
            indexedResources.addAll(properties);
        }
        jenaService.putDataModelToCore(modelURI, model);

        openSearchIndexer.bulkInsert(OpenSearchIndexer.OPEN_SEARCH_INDEX_RESOURCE,
                indexedResources.stream()
                .map(p -> ResourceMapper.mapToIndexResource(model, p))
                .toList());
    }

    @Operation(summary = "Update a class in a model")
    @ApiResponse(responseCode =  "200", description = "Class updated in model successfully")
    @PutMapping(value = "/{prefix}/{classIdentifier}", consumes = APPLICATION_JSON_VALUE)
    public void updateClass(@PathVariable String prefix, @PathVariable String classIdentifier, @RequestBody @ValidClass(updateClass = true) ClassDTO classDTO){
        logger.info("Updating class {}", classIdentifier);

        var graph = ModelConstants.SUOMI_FI_NAMESPACE + prefix;
        var classURI = graph + "#" + classIdentifier;
        if(!jenaService.doesResourceExistInGraph(graph, graph + "#" + classIdentifier)){
            throw new ResourceNotFoundException(classIdentifier);
        }

        var model = jenaService.getDataModel(graph);
        check(authorizationManager.hasRightToModel(prefix, model));

        var classResource = model.getResource(classURI);

        terminologyService.resolveConcept(classDTO.getSubject());
        ClassMapper.mapToUpdateClass(model, graph, classResource, classDTO, userProvider.getUser());
        jenaService.putDataModelToCore(graph, model);

        var indexClass = ResourceMapper.mapToIndexResource(model, classURI);
        openSearchIndexer.updateResourceToIndex(indexClass);
    }

    @Operation(summary = "Get a class from a data model")
    @ApiResponse(responseCode = "200", description = "Class found successfully")
    @GetMapping(value = "/{prefix}/{classIdentifier}", produces = APPLICATION_JSON_VALUE)
    public ClassInfoDTO getClass(@PathVariable String prefix, @PathVariable String classIdentifier){
        var modelURI = ModelConstants.SUOMI_FI_NAMESPACE + prefix;
        var classURI = modelURI + "#" + classIdentifier;
        if(!jenaService.doesResourceExistInGraph(modelURI , classURI)){
            throw new ResourceNotFoundException(classURI);
        }
        var model = jenaService.getDataModel(modelURI);
        if(model == null){
            throw new ResourceNotFoundException(modelURI);
        }
        var hasRightToModel = authorizationManager.hasRightToModel(prefix, model);

        var orgModel = jenaService.getOrganizations();
        var userMapper = hasRightToModel ? groupManagementService.mapUser() : null;
        var dto = ClassMapper.mapToClassDTO(model, modelURI, classIdentifier, orgModel,
                hasRightToModel, userMapper);

        if (MapperUtils.isOntology(model.getResource(modelURI))) {
            var classResources = jenaService.constructWithQuery(ClassMapper.getClassResourcesQuery(classURI, false));
            ClassMapper.addClassResourcesToDTO(classResources, dto);
        } else {
            ClassMapper.addNodeShapeResourcesToDTO(model, dto);
        }
        terminologyService.mapConceptToClass().accept(dto);
        return dto;
    }

    @Operation(summary = "Delete a class from a data model")
    @ApiResponse(responseCode = "200", description = "Class deleted successfully")
    @DeleteMapping(value = "/{prefix}/{classIdentifier}")
    public void deleteClass(@PathVariable String prefix, @PathVariable String classIdentifier){
        var modelURI = ModelConstants.SUOMI_FI_NAMESPACE + prefix;
        var classURI  = modelURI + "#" + classIdentifier;
        if(!jenaService.doesResourceExistInGraph(modelURI , classURI)){
            throw new ResourceNotFoundException(classURI);
        }
        var model = jenaService.getDataModel(modelURI);
        if(model == null){
            throw new ResourceNotFoundException(modelURI);
        }
        check(authorizationManager.hasRightToModel(prefix, model));
        jenaService.deleteResource(classURI);
        openSearchIndexer.deleteResourceFromIndex(classURI);
    }

    @Operation(summary = "Get an external class from imports")
    @ApiResponse(responseCode = "200", description = "External class found successfully")
    @GetMapping(value = "/external", produces = APPLICATION_JSON_VALUE)
    public ExternalClassDTO getExternalClass(@RequestParam String uri) {
        var namespace = NodeFactory.createURI(uri).getNameSpace();
        var model = jenaService.getNamespaceFromImports(namespace);

        var dto = ClassMapper.mapExternalClassToDTO(model, uri);
        var resources = jenaService.constructWithQueryImports(
                ClassMapper.getClassResourcesQuery(uri, true));

        ClassMapper.addExternalClassResourcesToDTO(resources, dto);

        return dto;
    }

    @Operation(summary = "Get all node shapes with given targetClass")
    @ApiResponse(responseCode = "200", description = "List of node shapes fetched successfully")
    @GetMapping(value = "/nodeshapes", produces = APPLICATION_JSON_VALUE)
    public List<IndexResource> getNodeShapes(@RequestParam String targetClass) throws IOException {
        var request = new ResourceSearchRequest();
        request.setStatus(Set.of(Status.VALID, Status.DRAFT));
        request.setTargetClass(targetClass);
        return searchIndexService
                .searchInternalResources(request, userProvider.getUser())
                .getResponseObjects();
    }
}
