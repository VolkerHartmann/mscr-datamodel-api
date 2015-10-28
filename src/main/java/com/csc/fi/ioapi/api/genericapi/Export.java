package com.csc.fi.ioapi.api.genericapi;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author malonen
 */
import com.csc.fi.ioapi.config.ApplicationProperties;
import com.csc.fi.ioapi.config.EndpointServices;
import com.csc.fi.ioapi.utils.LDHelper;
import com.csc.fi.ioapi.utils.ServiceDescriptionManager;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
 
/**
 * Root resource (exposed at "myresource" path)
 */
@Path("export")
@Api(value = "/export", description = "Export framed models")
public class Export {

    @Context ServletContext context;
    EndpointServices services = new EndpointServices();
        
  @GET
  @Produces("application/ld+json")
  @ApiOperation(value = "Get model from service", notes = "More notes about this method")
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid model supplied"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response json(@ApiParam(value = "Requested resource", defaultValue="default") @QueryParam("graph") String graph) {
   
      try {
            Client client = Client.create();

            WebResource webResource = client.resource(services.getCoreReadAddress())
                                      .queryParam("graph", graph);

            Builder builder = webResource.accept("application/ld+json");

            ClientResponse response = builder.get(ClientResponse.class);


             if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
               return Response.status(response.getStatus()).entity("{}").build();
            }
            
            ResponseBuilder rb;

            if(graph.equals("default")) {
                
                Object context = LDHelper.getDescriptionContext();        

                Object data;
                              try{
                    data = JsonUtils.fromInputStream(response.getEntityInputStream());

                     rb = Response.status(response.getStatus()); 
                try {
                    JsonLdOptions options = new JsonLdOptions();
                    Object framed = JsonLdProcessor.frame(data, context, options);                   
                    rb.entity(JsonUtils.toString(framed));   
                } catch (NullPointerException ex) {
                    Logger.getLogger(Export.class.getName()).log(Level.WARNING, null, "DEFAULT GRAPH IS NULL!");
                     return rb.entity(JsonUtils.toString(data)).build();
                } catch (JsonLdError ex) {
                    Logger.getLogger(Export.class.getName()).log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                  
                } catch (IOException ex) {
                    Logger.getLogger(Export.class.getName()).log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                
                
            } else {
                rb = Response.status(response.getStatus()); 
                rb.entity(response.getEntityInputStream());
            }
           
           return rb.build();
      
           
      } catch(UniformInterfaceException | ClientHandlerException ex) {
          Logger.getLogger(Export.class.getName()).log(Level.WARNING, "Expect the unexpected!", ex);
          return Response.serverError().entity("{}").build();
      } 

  }
 
}