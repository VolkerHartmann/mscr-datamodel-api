package fi.vm.yti.datamodel.api.v2.endpoint;

import fi.vm.yti.datamodel.api.security.AuthorizationManager;
import fi.vm.yti.datamodel.api.v2.dto.ClassDTO;
import fi.vm.yti.datamodel.api.v2.dto.ModelConstants;
import fi.vm.yti.datamodel.api.v2.endpoint.error.ResourceNotFoundException;
import fi.vm.yti.datamodel.api.v2.mapper.ClassMapper;
import fi.vm.yti.datamodel.api.v2.opensearch.index.OpenSearchIndexer;
import fi.vm.yti.datamodel.api.v2.service.JenaService;
import fi.vm.yti.datamodel.api.v2.validator.ValidClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static fi.vm.yti.security.AuthorizationException.check;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("v2/class")
@Tag(name = "Class" )
@Validated
public class ClassController {

    private final AuthorizationManager authorizationManager;
    private final JenaService jenaService;
    private final ClassMapper classMapper;
    private final OpenSearchIndexer openSearchIndexer;

    public ClassController(AuthorizationManager authorizationManager, JenaService jenaService, ClassMapper classMapper, OpenSearchIndexer openSearchIndexer){
        this.authorizationManager = authorizationManager;
        this.jenaService = jenaService;
        this.classMapper = classMapper;
        this.openSearchIndexer = openSearchIndexer;
    }

    @Operation(summary = "Add a class to a model")
    @ApiResponse(responseCode = "200", description = "Class added to model successfully")
    @PutMapping(value = "/{prefix}", consumes = APPLICATION_JSON_VALUE)
    public void createClass(@PathVariable String prefix, @RequestBody @ValidClass ClassDTO classDTO){
        var model = jenaService.getDataModel(ModelConstants.SUOMI_FI_NAMESPACE + prefix);
        if(model == null){
            throw new ResourceNotFoundException(prefix);
        }
        check(authorizationManager.hasRightToModel(prefix, model));

        var classURi = classMapper.createClassAndMapToModel(prefix, model, classDTO);
        jenaService.createDataModel(ModelConstants.SUOMI_FI_NAMESPACE + prefix, model);
        var indexClass = classMapper.mapToIndexClass(model, classURi);
        openSearchIndexer.createClassToIndex(indexClass);
    }

    @Operation(summary = "Get a class from a data model")
    @ApiResponse(responseCode = "200", description = "Class found successfully")
    @GetMapping(value = "/{prefix}/{classIdentifier}", produces = APPLICATION_JSON_VALUE)
    public ClassDTO getClass(@PathVariable String prefix, @PathVariable String classIdentifier){
        var model = jenaService.getDataModel(ModelConstants.SUOMI_FI_NAMESPACE + prefix);
        if(model == null){
            throw new ResourceNotFoundException(prefix);
        }
        return classMapper.mapToClassDTO(prefix, classIdentifier, model);
    }
}
