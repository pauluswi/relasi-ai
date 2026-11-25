package com.pswied.core.ai;

import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.knowledge.KnowledgeService.RetrievedChunk;

import java.util.List;

public interface LlmProvider {
    String generate(String userMessage, NlpResult analysis, Customer360 customerContext, List<RetrievedChunk> knowledgeContext);

    String providerId();
}
