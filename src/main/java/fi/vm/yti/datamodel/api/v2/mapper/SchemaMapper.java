package fi.vm.yti.datamodel.api.v2.mapper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import fi.vm.yti.datamodel.api.v2.service.StorageService;
import fi.vm.yti.datamodel.api.v2.service.StorageService.StoredFile;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.vm.yti.datamodel.api.v2.dto.ClassDTO;
import fi.vm.yti.datamodel.api.v2.dto.DCAP;
import fi.vm.yti.datamodel.api.v2.dto.Iow;
import fi.vm.yti.datamodel.api.v2.dto.MSCR;
import fi.vm.yti.datamodel.api.v2.dto.ModelConstants;
import fi.vm.yti.datamodel.api.v2.dto.ResourceDTO;
import fi.vm.yti.datamodel.api.v2.dto.ResourceType;
import fi.vm.yti.datamodel.api.v2.dto.SchemaDTO;
import fi.vm.yti.datamodel.api.v2.dto.SchemaFormat;
import fi.vm.yti.datamodel.api.v2.dto.SchemaInfoDTO;
import fi.vm.yti.datamodel.api.v2.dto.Status;
import fi.vm.yti.datamodel.api.v2.service.JenaService;
import fi.vm.yti.datamodel.api.v2.service.impl.PostgresStorageService;
import fi.vm.yti.datamodel.api.v2.service.StorageService;
import fi.vm.yti.datamodel.api.v2.dto.FileMetadata;



@Service
public class SchemaMapper {

	private final Logger log = LoggerFactory.getLogger(SchemaMapper.class);
	private final StorageService storageService;
	
	public SchemaMapper(PostgresStorageService storageService) {
		this.storageService = storageService;
	}
	
	
	
	public Model mapToJenaModel(String PID, SchemaDTO schemaDTO) {
		log.info("Mapping SchemaDTO to Jena Model");
		var model = ModelFactory.createDefaultModel();
		var modelUri = PID;
		// TODO: type of application profile?
		model.setNsPrefixes(ModelConstants.PREFIXES);
		Resource type = MSCR.SCHEMAGROUP;
		var creationDate = new XSDDateTime(Calendar.getInstance());
		var modelResource = model.createResource(modelUri).addProperty(RDF.type, type)
				.addProperty(OWL.versionInfo, schemaDTO.getStatus().name()).addProperty(DCTerms.identifier, PID)
				.addProperty(DCTerms.modified, ResourceFactory.createTypedLiteral(creationDate))
				.addProperty(DCTerms.created, ResourceFactory.createTypedLiteral(creationDate));

		schemaDTO.getLanguages().forEach(lang -> modelResource.addProperty(DCTerms.language, lang));

		modelResource.addProperty(Iow.contentModified, ResourceFactory.createTypedLiteral(creationDate));

		modelResource.addProperty(DCAP.preferredXMLNamespacePrefix, PID);
		modelResource.addProperty(DCAP.preferredXMLNamespace, modelResource);

		MapperUtils.addLocalizedProperty(schemaDTO.getLanguages(), schemaDTO.getLabel(), modelResource, RDFS.label,
				model);
		MapperUtils.addLocalizedProperty(schemaDTO.getLanguages(), schemaDTO.getDescription(), modelResource,
				RDFS.comment, model);

		addOrg(schemaDTO, modelResource);

		// addInternalNamespaceToDatamodel(modelDTO, modelResource, model);
		// addExternalNamespaceToDatamodel(modelDTO, model, modelResource);

		String prefix = MapperUtils.getMSCRPrefix(PID);
		model.setNsPrefix(prefix, modelUri + "#");
		
		modelResource.addProperty(MSCR.format, schemaDTO.getFormat().toString());
		

		return model;
	}
	
	public Model mapToUpdateJenaModel(String PID, SchemaInfoDTO dto) {
		var model = ModelFactory.createDefaultModel();
		return model;
	}

	public Model mapToJenaUpdateSchema(String pid, SchemaInfoDTO dto) {
		Model model = mapToUpdateJenaModel(pid, dto);		
		return model;
	}

	public SchemaInfoDTO mapToSchemaDTO(String PID, Model model) {

		var schemaInfoDTO = new SchemaInfoDTO();
		schemaInfoDTO.setPID(PID);

		var modelResource = model.getResource(PID);

		var status = Status.valueOf(MapperUtils.propertyToString(modelResource, OWL.versionInfo));
		schemaInfoDTO.setStatus(status);

		// Language
		schemaInfoDTO.setLanguages(MapperUtils.arrayPropertyToSet(modelResource, DCTerms.language));

		// Label
		schemaInfoDTO.setLabel(MapperUtils.localizedPropertyToMap(modelResource, RDFS.label));

		// Description
		schemaInfoDTO.setDescription(MapperUtils.localizedPropertyToMap(modelResource, RDFS.comment));

		schemaInfoDTO.setOrganization(MapperUtils.getUUID(MapperUtils.propertyToString(modelResource, DCTerms.contributor)));

		var created = modelResource.getProperty(DCTerms.created).getLiteral().getString();
		var modified = modelResource.getProperty(DCTerms.modified).getLiteral().getString();
		schemaInfoDTO.setCreated(created);
		schemaInfoDTO.setModified(modified);
		
		List<StoredFile> retrievedSchemaFiles = storageService.retrieveAllSchemaFiles(PID);
		Set<FileMetadata> fileMetadatas = new HashSet<>();
		retrievedSchemaFiles.forEach(file -> {
			fileMetadatas.add(new FileMetadata(file.contentType(), file.data().length));
		});
		schemaInfoDTO.setMetadataFiles(fileMetadatas);
		
		schemaInfoDTO.setPID(PID);
		schemaInfoDTO.setFormat(SchemaFormat.valueOf(MapperUtils.propertyToString(modelResource, MSCR.format)));
				
		return schemaInfoDTO;
	}

	/**
	 * Add organization to a schema 
	 * 
	 * @param modelDTO      Payload to get organizations from
	 * @param modelResource Model resource to add orgs to
	 */
	private void addOrg(SchemaDTO schemaDTO, Resource modelResource) {
		// TODO: Add org exists checks
		var orgRes = ResourceFactory.createResource(ModelConstants.URN_UUID + schemaDTO.getOrganization().toString());
		modelResource.addProperty(DCTerms.contributor, orgRes);
	}


	
}
