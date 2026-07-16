#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "llama.h"

typedef struct {
    struct llama_model *model;
    struct llama_context *ctx;
} LlamaState;

JNIEXPORT jlong JNICALL
Java_com_example_englishcoach_LLMEngine_nativeCreate(JNIEnv *env, jobject thiz, jstring modelPath, jint contextSize) {
    const char *path = (*env)->GetStringUTFChars(env, modelPath, NULL);

    struct llama_model_params model_params = llama_model_default_params();
    struct llama_model *model = llama_model_load_from_file(path, model_params);

    (*env)->ReleaseStringUTFChars(env, modelPath, path);

    if (model == NULL) {
        return 0;
    }

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = 512;

    struct llama_context *ctx = llama_init_from_model(model, ctx_params);

    if (ctx == NULL) {
        llama_model_free(model);
        return 0;
    }

    LlamaState *state = malloc(sizeof(LlamaState));
    state->model = model;
    state->ctx = ctx;

    return (jlong)(intptr_t)state;
}

JNIEXPORT jstring JNICALL
Java_com_example_englishcoach_LLMEngine_nativeGenerate(JNIEnv *env, jobject thiz, jlong modelPtr, jstring prompt, jint maxTokens) {
    LlamaState *state = (LlamaState *)(intptr_t)modelPtr;

    if (state == NULL || state->ctx == NULL) {
        return (*env)->NewStringUTF(env, "Error: model not loaded");
    }

    const char *promptStr = (*env)->GetStringUTFChars(env, prompt, NULL);

    llama_token tokens[4096];
    int n_tokens = llama_tokenize(state->ctx, promptStr, strlen(promptStr), tokens, 4096, true, true);

    (*env)->ReleaseStringUTFChars(env, prompt, promptStr);

    if (n_tokens < 0) {
        return (*env)->NewStringUTF(env, "Error: tokenization failed");
    }

    llama_kv_cache_clear(state->ctx);

    if (llama_decode(state->ctx, llama_batch_get_one(tokens, n_tokens))) {
        return (*env)->NewStringUTF(env, "Error: prompt evaluation failed");
    }

    char response[4096] = {0};
    int resp_len = 0;

    for (int i = 0; i < maxTokens && resp_len < 4095; i++) {
        llama_token new_token = llama_sampler_sample(NULL, state->ctx, -1);

        if (llama_token_is_eog(state->model, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(state->model, new_token, buf, sizeof(buf) - 1, 0, true);
        if (n > 0) {
            buf[n] = 0;
            int copy_len = n;
            if (resp_len + copy_len > 4095) copy_len = 4095 - resp_len;
            memcpy(response + resp_len, buf, copy_len);
            resp_len += copy_len;
        }

        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(state->ctx, batch)) {
            break;
        }
    }

    response[resp_len] = 0;
    return (*env)->NewStringUTF(env, response);
}

JNIEXPORT void JNICALL
Java_com_example_englishcoach_LLMEngine_nativeDestroy(JNIEnv *env, jobject thiz, jlong modelPtr) {
    LlamaState *state = (LlamaState *)(intptr_t)modelPtr;

    if (state != NULL) {
        if (state->ctx) llama_free(state->ctx);
        if (state->model) llama_model_free(state->model);
        free(state);
    }
}
