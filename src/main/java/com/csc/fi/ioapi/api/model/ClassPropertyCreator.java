/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1 
 */
package com.csc.fi.ioapi.api.model;

import com.csc.fi.ioapi.config.ApplicationProperties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import com.csc.fi.ioapi.config.EndpointServices;
import com.csc.fi.ioapi.utils.ErrorMessage;
import com.csc.fi.ioapi.utils.GraphManager;
import com.csc.fi.ioapi.utils.JerseyFusekiClient;
import com.csc.fi.ioapi.utils.LDHelper;
import com.csc.fi.ioapi.utils.NamespaceManager;
import org.apache.jena.query.ParameterizedSparqlString;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIException;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.util.SplitIRI;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("classProperty")
@Api(value = "/classProperty", description = "Operations about property")
public class ClassPropertyCreator {

    @Context ServletContext context;
    private EndpointServices services = new EndpointServices();
    private static final Logger logger = Logger.getLogger(ClassPropertyCreator.class.getName());
    
    
  @GET
  @Produces("application/ld+json")
  @ApiOperation(value = "Get property from model", notes = "More notes about this method")
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid model supplied"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response json(
	@ApiParam(value = "Predicate ID", required = true) @QueryParam("predicateID") String predicateID,
        @ApiParam(value = "Predicate type", allowableValues="owl:DatatypeProperty,owl:ObjectProperty") @QueryParam("type") String type) {

      IRI predicateIRI;
      IRI typeIRI = null;
        
      String service;
      String queryString;
      ParameterizedSparqlString pss = new ParameterizedSparqlString();
      pss.setNsPrefixes(LDHelper.PREFIX_MAP);

      try {         
         IRIFactory iri = IRIFactory.semanticWebImplementation();
         predicateIRI = iri.construct(predicateID);
         
         if(predicateID.startsWith(ApplicationProperties.getDefaultDomain())) {
         /* Local predicate */
           if(!GraphManager.isExistingGraph(predicateIRI)) {
              return Response.status(403).entity(ErrorMessage.INVALIDIRI).build();
           }
           
        service = services.getCoreSparqlAddress();
              
        queryString = "CONSTRUCT { "
              + "?uuid sh:predicate ?predicate . "
              + "?uuid dcterms:type ?predicateType . "
              + "?uuid dcterms:type ?externalType . "
              + "?uuid dcterms:created ?creation . "
              + "?uuid dcterms:identifier ?localIdentifier . "
              + "?uuid rdfs:label ?label . "
              + "?uuid rdfs:comment ?comment . "
              + "?uuid sh:valueShape ?valueClass . "
              + "?uuid sh:datatype ?datatype . } "
              + "WHERE { "
              + "BIND(now() as ?creation) "
              + "BIND(UUID() as ?uuid) "
              + "OPTIONAL { "
              + "GRAPH ?predicate { "
              + "?predicate rdfs:label ?label .  "
              + "?predicate a ?predicateType . "
              + "OPTIONAL{ ?predicate rdfs:comment ?comment . } "
              + "OPTIONAL{ ?predicate a owl:DatatypeProperty . "
              + "?predicate rdfs:range ?datatype . } "
              + "OPTIONAL { ?predicate a owl:ObjectProperty . "
              + "?predicate rdfs:range ?valueClass . }}}}";

      pss.setCommandText(queryString);
      pss.setIri("predicate", predicateIRI);
      pss.setLiteral("localIdentifier", SplitIRI.localname(predicateID));
          
      } else {
             
         /* External predicate */
         String predicateType = NamespaceManager.getExternalPredicateType(predicateIRI);
         
         if(predicateType==null && type!=null && !type.equals("undefined")) {
             String typeURI = type.replace("owl:", "http://www.w3.org/2002/07/owl#");
             typeIRI = iri.construct(typeURI);
          } else {
             if(predicateType==null) return Response.status(403).entity(ErrorMessage.INVALIDPARAMETER).build();
             else typeIRI = iri.construct(predicateType);
         }
           
         service = services.getImportsSparqlAddress();
      
         queryString = "CONSTRUCT { "
              + "?uuid sh:predicate ?predicate . "
              + "?uuid dcterms:type ?predicateType . "
              + "?uuid dcterms:created ?creation . "
              + "?uuid dcterms:identifier ?localIdentifier . "
              + "?uuid rdfs:label ?label . "
              + "?uuid rdfs:comment ?comment . "
              + "?uuid sh:valueShape ?valueClass . "
              + "?uuid sh:datatype ?prefDatatype . } "
              + "WHERE { "
              + "BIND(now() as ?creation) "
              + "BIND(UUID() as ?uuid) "
              + "OPTIONAL { "
              + "?predicate rdfs:label ?label .  " 
              + "?predicate a ?predicateType . "              
              + "OPTIONAL { ?predicate rdfs:comment ?comment . } "
              + "OPTIONAL { ?predicate a owl:DatatypeProperty . "
              + "?predicate rdfs:range ?datatype . "
              + "BIND(IF(?datatype=rdfs:Literal,xsd:string,?datatype) as ?prefDatatype) } "
              + "OPTIONAL { ?predicate a owl:ObjectProperty . "
              + "?predicate rdfs:range ?valueClass . }}"
              + "}";

            pss.setCommandText(queryString);
            pss.setIri("predicate", predicateIRI);
            pss.setLiteral("localIdentifier", SplitIRI.localname(predicateID));
            pss.setIri("predicateType", typeIRI);

         }
        } catch (IRIException e) {
           return Response.status(403).entity(ErrorMessage.INVALIDIRI).build();
      }
       
      //logger.info(""+SplitIRI.localname(predicateID));
      
      return JerseyFusekiClient.constructGraphFromService(pss.toString(), service);

  }
  
}
