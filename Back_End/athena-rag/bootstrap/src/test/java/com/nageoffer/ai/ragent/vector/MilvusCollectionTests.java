

package com.nageoffer.ai.ragent.vector;

import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceSpec;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class MilvusCollectionTests {

    private final VectorStoreAdmin vectorStoreAdmin;

    private static final String COLLECTION_NAME = "test_collection";

    @Test
    public void createCollection() {
        VectorSpaceId spaceId = VectorSpaceId.builder()
                .logicalName(COLLECTION_NAME)
                .build();
        boolean exists = vectorStoreAdmin.vectorSpaceExists(spaceId);
        if (exists) {
            log.info("Vector space already exists.");
            return;
        }

        vectorStoreAdmin.ensureVectorSpace(VectorSpaceSpec.builder()
                .spaceId(spaceId)
                .remark("Ragent 知识库向量集合")
                .build());
    }
}
