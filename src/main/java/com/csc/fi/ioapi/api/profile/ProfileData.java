package com.csc.fi.ioapi.api.profile;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author malonen
 */
import com.csc.fi.ioapi.config.Endpoint;
import com.csc.fi.ioapi.utils.LDHelper;
import com.csc.fi.ioapi.utils.ServiceDescriptionManager;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import com.github.jsonldjava.core.JsonLdApi;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import static com.github.jsonldjava.core.JsonLdProcessor.compact;
import com.github.jsonldjava.utils.JsonUtils;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.QueryParam;
import com.hp.hpl.jena.query.DatasetAccessor ;
import com.hp.hpl.jena.query.DatasetAccessorFactory ;
import com.hp.hpl.jena.rdf.model.Model;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource.Builder;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import static javax.ws.rs.HttpMethod.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
 
/**
 * Root resource (exposed at "myresource" path)
 */
@Path("profile-data")
@Api(value = "/profile-data", description = "Operations about data")
public class ProfileData {

    @Context ServletContext context;
        
    public String ProfileDataEndpoint() {
      return Endpoint.getEndpoint()+"/profile/data";
    }
    
    public String ProfileSparqlUpdateEndpoint() {
       return Endpoint.getEndpoint()+"/profile/update";
    }
    
    
  @GET
  @Produces("application/ld+json")
  @ApiOperation(value = "Get model from service", notes = "More notes about this method")
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid model supplied"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response json(@ApiParam(value = "Requested resource", defaultValue="default") @QueryParam("graph") String graph) {
 
      String service = ProfileDataEndpoint();
      
      try {
            Client client = Client.create();

            WebResource webResource = client.resource(service)
                                      .queryParam("graph", graph);

            Builder builder = webResource.accept("application/ld+json");

            ClientResponse response = builder.get(ClientResponse.class);


            if (response.getStatus() != 200) {
               Logger.getLogger(ProfileData.class.getName()).log(Level.INFO, response.getStatus()+" from SERVICE "+service+" and GRAPH "+graph);
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
                    Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, null, "DEFAULT GRAPH IS NULL!");
                     return rb.entity(JsonUtils.toString(data)).build();
                } catch (JsonLdError ex) {
                    Logger.getLogger(ProfileData.class.getName()).log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                  
                } catch (IOException ex) {
                    Logger.getLogger(ProfileData.class.getName()).log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                
                
            } else {
                rb = Response.status(response.getStatus()); 
                rb.entity(response.getEntityInputStream());
            }
           
           return rb.build();
      
           
      } catch(UniformInterfaceException | ClientHandlerException ex) {
          Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, "Expect the unexpected!", ex);
          return Response.serverError().entity("{}").build();
      } 

  }
  
   /**
     * Replaces Graph in given service
     * @returns empty Response
     */
  @POST
  @ApiOperation(value = "Updates graph in service and writes service description to default", notes = "PUT Body should be json-ld")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Graph is saved"),
      @ApiResponse(code = 400, message = "Invalid graph supplied"),
      @ApiResponse(code = 403, message = "Illegal graph parameter"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Bad data?") 
  })
  public Response postJson(
          @ApiParam(value = "New graph in application/ld+json", required = true) 
                String body, @ApiParam(value = "Graph to overwrite", required = true) 
          @QueryParam("graph") 
                String graph) {
      
       if(graph.equals("default")) {
            return Response.status(403).build();
       } 
       try {

           String service = ProfileDataEndpoint();

           if(!(graph.equals("undefined") || graph.equals("default"))) {
               ServiceDescriptionManager.updateGraphDescription(ProfileSparqlUpdateEndpoint(), graph);
           }

            Client client = Client.create();

            WebResource webResource = client.resource(service)
                                      .queryParam("graph", graph);

            WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
            ClientResponse response = builder.put(ClientResponse.class,body);

            if (response.getStatus() != 204) {
               Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, graph+" was not updated! Status "+response.getStatus());
               return Response.status(response.getStatus()).build();
            }

            Logger.getLogger(ProfileData.class.getName()).log(Level.INFO, graph+" updated sucessfully!");
            return Response.status(204).build();

      } catch(UniformInterfaceException | ClientHandlerException ex) {
        Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, "Expect the unexpected!", ex);
        return Response.status(400).build();
      }
  }
  
  @PUT
  @ApiOperation(value = "Create new graph and update service description", notes = "PUT Body should be json-ld")
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Graph is created"),
      @ApiResponse(code = 204, message = "Graph is saved"),
      @ApiResponse(code = 403, message = "Illegal graph parameter"),
      @ApiResponse(code = 400, message = "Invalid graph supplied"),
      @ApiResponse(code = 500, message = "Bad data?") 
  })
  public Response putJson(
          @ApiParam(value = "New graph in application/ld+json", required = true) 
                String body, 
          @ApiParam(value = "Graph to overwrite", required = true) 
          @QueryParam("graph") 
                String graph,
          @ApiParam(value = "Group", required = true) 
          @QueryParam("group") 
                String group) {
      
       if(graph.equals("default")) {
           return Response.status(403).build();
       }
       try {

            String service = ProfileDataEndpoint();

           if(!(graph.equals("undefined") || graph.equals("default"))) {
               ServiceDescriptionManager.createGraphDescription(ProfileSparqlUpdateEndpoint(), graph, group);
           }

            Client client = Client.create();

            WebResource webResource = client.resource(service)
                                      .queryParam("graph", graph);

            WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
            ClientResponse response = builder.put(ClientResponse.class,body);

            if (response.getStatus() != 204) {
               Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, graph+" was not updated! Status "+response.getStatus());
               return Response.status(response.getStatus()).build();
            }

            Logger.getLogger(ProfileData.class.getName()).log(Level.INFO, graph+" updated sucessfully!");
            return Response.status(204).build();

      } catch(UniformInterfaceException | ClientHandlerException ex) {
        Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, "Expect the unexpected!", ex);
        return Response.status(400).build();
      }
  }
  
  
  @DELETE
  @ApiOperation(value = "Delete graph from service and service description", notes = "Delete graph")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Graph is deleted"),
      @ApiResponse(code = 403, message = "Illegal graph parameter"),
      @ApiResponse(code = 404, message = "No such graph")
  })
  public Response deleteJson(
          @ApiParam(value = "Graph to be deleted", required = true) 
          @QueryParam("graph") 
                String graph) {
      
       if(graph.equals("default")) {
           return Response.status(403).build();
       }

       try {

            String service = ProfileDataEndpoint();

            Client client = Client.create();

            WebResource webResource = client.resource(service)
                                      .queryParam("graph", graph);

            WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
            ClientResponse response = builder.delete(ClientResponse.class);

            if (response.getStatus() != 204) {
               Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, graph+" was not deleted! Status "+response.getStatus());
               return Response.status(response.getStatus()).build();
            }

            if(!(graph.equals("undefined") || graph.equals("default"))) {
               ServiceDescriptionManager.deleteGraphDescription(ProfileSparqlUpdateEndpoint(), graph);
           }

            Logger.getLogger(ProfileData.class.getName()).log(Level.INFO, graph+" deleted successfully!");
            return Response.status(204).build();

      } catch(UniformInterfaceException | ClientHandlerException ex) {
            Logger.getLogger(ProfileData.class.getName()).log(Level.WARNING, "Expect the unexpected!", ex);
            return Response.status(400).build();
      }
  }
  
  
  
}