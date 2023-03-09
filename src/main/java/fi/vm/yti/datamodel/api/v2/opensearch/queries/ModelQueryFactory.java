package fi.vm.yti.datamodel.api.v2.opensearch.queries;

import fi.vm.yti.datamodel.api.v2.dto.ModelType;
import fi.vm.yti.datamodel.api.v2.dto.Status;
import fi.vm.yti.datamodel.api.v2.opensearch.dto.ModelSearchRequest;
import fi.vm.yti.datamodel.api.v2.opensearch.index.OpenSearchIndexer;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOptionsBuilders;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.mapping.FieldType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.ArrayList;
import java.util.UUID;

import static fi.vm.yti.datamodel.api.v2.opensearch.OpenSearchUtil.logPayload;

public class ModelQueryFactory {

    private ModelQueryFactory() {
        //only static functions here
    }

    public static SearchRequest createModelQuery(ModelSearchRequest request){
        var must = new ArrayList<Query>();
        var should = new ArrayList<Query>();

        var incompleteFrom = request.getIncludeIncompleteFrom();
        if(incompleteFrom != null && !incompleteFrom.isEmpty()){
            var incompleteFromQuery = QueryFactoryUtils.termsQuery("contributor", incompleteFrom.stream().map(UUID::toString).toList());
            should.add(incompleteFromQuery);
        }

        var incompleteStatusQuery = QueryFactoryUtils.hideIncompleteStatusQuery();
        should.add(incompleteStatusQuery);

        var queryString = request.getQuery();
        if(queryString != null && !queryString.isBlank()){
            must.add(QueryFactoryUtils.labelQuery(queryString));
        }

        var modelType = request.getType();
        if(modelType != null && !modelType.isEmpty()){
            var modelTypeQuery = QueryFactoryUtils.termsQuery("type", modelType.stream().map(ModelType::name).toList());
            must.add(modelTypeQuery);
        }

        var groups = request.getGroups();
        if(groups != null && !groups.isEmpty()){
            var groupsQuery = QueryFactoryUtils.termsQuery("isPartOf", groups.stream().toList());
            must.add(groupsQuery);
        }

        var organizations = request.getOrganizations();
        if(organizations != null && !organizations.isEmpty()){
            var orgsQuery = QueryFactoryUtils.termsQuery("contributor", organizations.stream().map(UUID::toString).toList());
            must.add(orgsQuery);
        }

        var language = request.getLanguage();
        if(language != null && !language.isBlank()) {
            var languageQuery = QueryFactoryUtils.termQuery("language", language);
            must.add(languageQuery);
        }

        var status = request.getStatus();
        if(status != null && !status.isEmpty()){
            var statusQuery = QueryFactoryUtils.termsQuery("status", status.stream().map(Status::name).toList());
            must.add(statusQuery);
        }

        var finalQuery = QueryBuilders.bool()
                .must(must)
                .minimumShouldMatch("1")
                .should(should)
                .build();

        var sortLang = request.getSortLang() != null ? request.getSortLang() : QueryFactoryUtils.DEFAULT_SORT_LANG;
        var sort = SortOptionsBuilders.field()
                .field("label." + sortLang + ".keyword")
                .order(SortOrder.Asc)
                .unmappedType(FieldType.Keyword)
                .build();

        var sr = new SearchRequest.Builder()
                .index(OpenSearchIndexer.OPEN_SEARCH_INDEX_MODEL)
                .size(QueryFactoryUtils.pageSize(request.getPageSize()))
                .from(QueryFactoryUtils.pageFrom(request.getPageFrom()))
                .sort(SortOptions.of(s -> s.field(sort)))
                .query(finalQuery._toQuery())
                .build();

        logPayload(sr);
        return sr;
    }

}