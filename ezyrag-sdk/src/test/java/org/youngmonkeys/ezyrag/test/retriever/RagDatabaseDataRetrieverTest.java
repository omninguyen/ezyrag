/*
 * Copyright 2026 youngmonkeys.org
 * 
 * Licensed under the ezyplatform, Version 1.0.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://youngmonkeys.org/licenses/ezyplatform-1.0.0.txt
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.youngmonkeys.ezyrag.test.retriever;

import com.tvd12.test.assertion.Asserts;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.youngmonkeys.ezyrag.constant.RagDataRetrieverName;
import org.youngmonkeys.ezyrag.converter.EzyRagModelToModelConverter;
import org.youngmonkeys.ezyrag.model.RagDataChunkModel;
import org.youngmonkeys.ezyrag.model.RagDocumentModel;
import org.youngmonkeys.ezyrag.model.RagVectorSearchResultModel;
import org.youngmonkeys.ezyrag.retriever.RagDatabaseDataRetriever;
import org.youngmonkeys.ezyrag.service.RagDataChunkMetaService;
import org.youngmonkeys.ezyrag.service.RagDataChunkService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RagDatabaseDataRetrieverTest {

    @Mock
    private RagDataChunkService dataChunkService;

    @Mock
    private RagDataChunkMetaService dataChunkMetaService;

    private RagDatabaseDataRetriever sut;

    @BeforeMethod
    public void init() {
        MockitoAnnotations.initMocks(this);
        sut = new RagDatabaseDataRetriever(
            dataChunkService,
            dataChunkMetaService,
            new EzyRagModelToModelConverter()
        );
    }

    @Test
    public void retrieveKeepsVectorSearchResultOrder() {
        // given
        List<RagVectorSearchResultModel> result = Arrays.asList(
            searchResult(3L, 0.93F),
            searchResult(1L, 0.81F),
            searchResult(2L, 0.72F)
        );
        // the database returns rows in primary key order,
        // which is not the relevance order
        when(dataChunkService.getDataChunksByIds(any()))
            .thenReturn(
                Arrays.asList(
                    chunk(1L, "chunk 1"),
                    chunk(2L, "chunk 2"),
                    chunk(3L, "chunk 3")
                )
            );
        when(dataChunkMetaService.getDataChunkMetaMapByIds(any()))
            .thenReturn(Collections.emptyMap());

        // when
        List<RagDocumentModel> actual = sut.retrieve(result);

        // then
        Asserts.assertEquals(actual.size(), 3);
        Asserts.assertEquals(actual.get(0).getContent(), "chunk 3");
        Asserts.assertEquals(actual.get(1).getContent(), "chunk 1");
        Asserts.assertEquals(actual.get(2).getContent(), "chunk 2");
    }

    @Test
    public void retrieveKeepsOrderWhenDatabaseReturnsReversedRows() {
        // given
        List<RagVectorSearchResultModel> result = Arrays.asList(
            searchResult(1L, 0.99F),
            searchResult(2L, 0.55F)
        );
        when(dataChunkService.getDataChunksByIds(any()))
            .thenReturn(
                Arrays.asList(
                    chunk(2L, "chunk 2"),
                    chunk(1L, "chunk 1")
                )
            );
        when(dataChunkMetaService.getDataChunkMetaMapByIds(any()))
            .thenReturn(Collections.emptyMap());

        // when
        List<RagDocumentModel> actual = sut.retrieve(result);

        // then
        Asserts.assertEquals(actual.get(0).getContent(), "chunk 1");
        Asserts.assertEquals(actual.get(1).getContent(), "chunk 2");
    }

    @Test
    public void retrieveAttachesMetadataOfMatchingChunk() {
        // given
        List<RagVectorSearchResultModel> result = Arrays.asList(
            searchResult(3L, 0.93F),
            searchResult(1L, 0.81F),
            searchResult(2L, 0.72F)
        );
        when(dataChunkService.getDataChunksByIds(any()))
            .thenReturn(
                Arrays.asList(
                    chunk(1L, "chunk 1"),
                    chunk(2L, "chunk 2"),
                    chunk(3L, "chunk 3")
                )
            );
        Map<Long, Map<String, String>> metadataMapByChunkId =
            new HashMap<>();
        metadataMapByChunkId.put(
            1L,
            Collections.singletonMap("title", "title 1")
        );
        metadataMapByChunkId.put(
            3L,
            Collections.singletonMap("title", "title 3")
        );
        when(dataChunkMetaService.getDataChunkMetaMapByIds(any()))
            .thenReturn(metadataMapByChunkId);

        // when
        List<RagDocumentModel> actual = sut.retrieve(result);

        // then
        Asserts.assertEquals(
            actual.get(0).getMetadata().get("title"),
            "title 3"
        );
        Asserts.assertEquals(
            actual.get(1).getMetadata().get("title"),
            "title 1"
        );
        Asserts.assertTrue(actual.get(2).getMetadata().isEmpty());
    }

    @Test
    public void retrieveIgnoresChunkNotFoundInDatabase() {
        // given
        List<RagVectorSearchResultModel> result = Arrays.asList(
            searchResult(3L, 0.93F),
            searchResult(9L, 0.85F),
            searchResult(1L, 0.81F)
        );
        // the chunk 9 was deleted from the database
        when(dataChunkService.getDataChunksByIds(any()))
            .thenReturn(
                Arrays.asList(
                    chunk(1L, "chunk 1"),
                    chunk(3L, "chunk 3")
                )
            );
        when(dataChunkMetaService.getDataChunkMetaMapByIds(any()))
            .thenReturn(Collections.emptyMap());

        // when
        List<RagDocumentModel> actual = sut.retrieve(result);

        // then
        Asserts.assertEquals(actual.size(), 2);
        Asserts.assertEquals(actual.get(0).getContent(), "chunk 3");
        Asserts.assertEquals(actual.get(1).getContent(), "chunk 1");
    }

    @Test
    public void retrieveWithEmptyResultReturnsEmptyList() {
        // when
        List<RagDocumentModel> actual = sut.retrieve(
            Collections.emptyList()
        );

        // then
        Asserts.assertTrue(actual.isEmpty());
        verify(dataChunkService, never()).getDataChunksByIds(any());
        verify(dataChunkMetaService, never())
            .getDataChunkMetaMapByIds(any());
    }

    @Test
    public void retrieveWithNoChunkFoundReturnsEmptyList() {
        // given
        List<RagVectorSearchResultModel> result = Collections
            .singletonList(searchResult(1L, 0.5F));
        when(dataChunkService.getDataChunksByIds(any()))
            .thenReturn(Collections.emptyList());
        when(dataChunkMetaService.getDataChunkMetaMapByIds(any()))
            .thenReturn(Collections.emptyMap());

        // when
        List<RagDocumentModel> actual = sut.retrieve(result);

        // then
        Asserts.assertTrue(actual.isEmpty());
    }

    @Test
    public void getNameReturnsDatabase() {
        Asserts.assertEquals(
            sut.getName(),
            RagDataRetrieverName.DATABASE.toString()
        );
    }

    private RagVectorSearchResultModel searchResult(
        long chunkId,
        float score
    ) {
        return RagVectorSearchResultModel.builder()
            .chunkId(chunkId)
            .score(score)
            .build();
    }

    private RagDataChunkModel chunk(long id, String content) {
        return RagDataChunkModel.builder()
            .id(id)
            .sourceType("article")
            .sourceId(id * 10)
            .chunkIndex(0)
            .content(content)
            .build();
    }
}
