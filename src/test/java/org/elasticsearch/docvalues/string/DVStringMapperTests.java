/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.docvalues.string;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.Names;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ElasticsearchIntegrationTest.ClusterScope;
import org.elasticsearch.test.ElasticsearchIntegrationTest.Scope;
import org.junit.Test;

/**
 */
@ClusterScope(scope= Scope.SUITE, numDataNodes =1, numClientNodes =1)
public class DVStringMapperTests extends AbstractSearchTests {

	private String getIndex() {
		return "testdvstring";
	}
	
	private static String nextRandomName() {
	    return Names.randomNodeName(Names.class.getResource("/config/names.txt"));
	}
	
	private void prepareTest() throws Exception {
		// Create a new index
		String mapping = XContentFactory.jsonBuilder()
		.startObject()
			.startObject("superhero")
				.startObject("properties")
					.startObject("name").field("type", "dvstring").field("doc_values", true)
					    /*.field("doc_values", true)*//*.field("index", "not_analyzed")*/
					.endObject()
				.endObject()
			.endObject()
		.endObject().string();

		assertAcked(prepareCreate(getIndex()).addMapping("superhero", mapping));

		List<IndexRequestBuilder> indexBuilders = new ArrayList<IndexRequestBuilder>();
		for (int i = 0; i < 50; i++) {
	        String nextName = nextRandomName();
		    indexBuilders.add(client().prepareIndex(getIndex(), "superhero", i + "").setSource(XContentFactory.jsonBuilder()
	            .startObject()
	                .field("name", nextName)
	            .endObject()));
	        
		    
		}
		indexRandom(false, false, false, indexBuilders);
		indexBuilders.clear();
        
        super.flushAndRefresh();
        super.ensureGreen(getIndex());
	}

	@Test
	public void testIt() throws Exception {
		prepareTest();
		SearchHit[] hits = search();
		String previousName = null;
        for (SearchHit hit : hits) {
            System.err.println(hit.getId() + " - " + hit.getSource().get("name"));
          for (Object o : hit.getSortValues()) {
              System.err.println("   " + o);
          }
            String name = (String)hit.getSource().get("name");
            if (previousName != null) {
                assertTrue(previousName.compareToIgnoreCase(name) < 0);
            }
            previousName = name;
        }
	}

	/**
	 */
	private SearchHit[] search() throws IOException {
		SearchResponse searchResponse = client().prepareSearch(getIndex())
				.setQuery(QueryBuilders.matchAllQuery())
				.addSort("name", SortOrder.ASC)
				.setExplain(true)
				.setSize(50)
				.setTypes("superhero")
				.execute().actionGet();
		
		// Retrieve record and check
		assertTrue(searchResponse.status().getStatus() == 200);
		return searchResponse.getHits().getHits();
	}
	
}
