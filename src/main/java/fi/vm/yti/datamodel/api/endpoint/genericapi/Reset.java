package fi.vm.yti.datamodel.api.endpoint.genericapi;

import fi.vm.yti.datamodel.api.security.AuthorizationManager;
import fi.vm.yti.datamodel.api.service.GraphManager;
import fi.vm.yti.datamodel.api.service.JerseyResponseManager;
import fi.vm.yti.datamodel.api.service.NamespaceManager;
import fi.vm.yti.datamodel.api.service.RHPOrganizationManager;
import fi.vm.yti.migration.Migration;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Component
@Path("reset")
@Api(tags = {"Admin"}, description = "DROP ALL and Recover")
public class Reset {

    private final GraphManager graphManager;
    private final RHPOrganizationManager rhpOrganizationManager;
    private final NamespaceManager namespaceManager;
    private final AuthorizationManager authorizationManager;
    private final JerseyResponseManager jerseyResponseManager;
    private final Migration migrationManager;

    Reset(GraphManager graphManager,
          RHPOrganizationManager rhpOrganizationManager,
          NamespaceManager namespaceManager,
          AuthorizationManager authorizationManager,
          JerseyResponseManager jerseyResponseManager,
          Migration migrationManager) {

        this.graphManager = graphManager;
        this.rhpOrganizationManager = rhpOrganizationManager;
        this.namespaceManager = namespaceManager;
        this.authorizationManager = authorizationManager;
        this.jerseyResponseManager = jerseyResponseManager;
        this.migrationManager = migrationManager;
    }

    @GET
    @ApiOperation(value = "Drops everything")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK")
    })
    public Response drop() {

        if (!authorizationManager.hasRightToDropDatabase()) {
            return jerseyResponseManager.unauthorized();
        }

        graphManager.deleteGraphs();
        migrationManager.migrate();
        rhpOrganizationManager.initOrganizationsFromRHP();
        namespaceManager.addDefaultNamespacesToCore();

        return Response.ok().build();
    }
}
