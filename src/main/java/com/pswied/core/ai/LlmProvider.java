package com.pswied.core.ai;

import com.pswied.core.crm.CrmModels.Customer360;

public interface LlmProvider {
    String generate(String userMessage, NlpResult analysis, Customer360 customerContext);

    String providerId();
}
