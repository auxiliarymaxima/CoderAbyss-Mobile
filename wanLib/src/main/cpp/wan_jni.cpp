#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <string>
#include <fstream>
#include <stdexcept>
#include "stable-diffusion.h"

static std::mutex state_mutex;
static sd_ctx_t* active = nullptr;
static std::atomic<bool> cancelled{false};
static std::string status = "Ready";
static std::string last_error;
static void update(const std::string& text) { std::lock_guard<std::mutex> lock(state_mutex); status = text; }
static void log_cb(sd_log_level_t level, const char* text, void*) {
    if (level >= SD_LOG_WARN) __android_log_write(ANDROID_LOG_WARN, "CoderWan", text);
    if (level == SD_LOG_ERROR) { std::lock_guard<std::mutex> lock(state_mutex); last_error = std::string(text).substr(0, 600); }
}
static void progress_cb(int step, int steps, float, void*) {
    update("Generating frame sequence: step " + std::to_string(step) + "/" + std::to_string(steps));
}
static std::string value(JNIEnv* env, jstring text) {
    const char* chars = env->GetStringUTFChars(text, nullptr);
    std::string result(chars); env->ReleaseStringUTFChars(text, chars); return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_coderabyss_wan_WanNative_status(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(state_mutex); return env->NewStringUTF(status.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_coderabyss_wan_WanNative_cancel(JNIEnv*, jobject) {
    cancelled = true;
    std::lock_guard<std::mutex> lock(state_mutex);
    if (active) sd_cancel_generation(active, SD_CANCEL_ALL);
}
extern "C" JNIEXPORT void JNICALL
Java_com_coderabyss_wan_WanNative_reset(JNIEnv*, jobject) { cancelled = false; }
extern "C" JNIEXPORT jint JNICALL
Java_com_coderabyss_wan_WanNative_generate(JNIEnv* env, jobject, jstring directory, jstring prompt,
                                          jstring output, jint width, jint height, jint count, jint steps) {
    sd_ctx_t* ctx = nullptr;
    sd_image_t* frames = nullptr;
    int actual = 0;
    auto cleanup = [&]() {
        { std::lock_guard<std::mutex> lock(state_mutex); active = nullptr; }
        if (frames) { free_sd_images(frames, actual); frames = nullptr; }
        if (ctx) { free_sd_ctx(ctx); ctx = nullptr; }
    };
    try {
        auto dir = value(env, directory), text = value(env, prompt), dest = value(env, output);
        auto model = dir + "/Wan2.1-T2V-1.3B-Q4_K_M.gguf";
        auto encoder = dir + "/umt5-xxl-encoder-Q4_K_M.gguf";
        auto vae = dir + "/wan_2.1_vae.safetensors";
        sd_set_log_callback(log_cb, nullptr);
        sd_set_progress_callback(progress_cb, nullptr);
        { std::lock_guard<std::mutex> lock(state_mutex); last_error.clear(); }
        if (cancelled) throw std::runtime_error("Generation cancelled");
        update("Loading local Wan, text encoder and video decoder...");
        sd_ctx_params_t settings; sd_ctx_params_init(&settings);
        settings.diffusion_model_path = model.c_str();
        settings.t5xxl_path = encoder.c_str();
        settings.vae_path = vae.c_str();
        settings.backend = "CPU";
        settings.params_backend = "CPU";
        settings.n_threads = 4;
        settings.enable_mmap = true;
        settings.diffusion_flash_attn = true;
        settings.eager_load = false;
        ctx = new_sd_ctx(&settings);
        if (!ctx) throw std::runtime_error("Wan could not load. Check available RAM and the model files.");
        { std::lock_guard<std::mutex> lock(state_mutex); active = ctx; }
        if (cancelled) throw std::runtime_error("Generation cancelled");
        sd_vid_gen_params_t params; sd_vid_gen_params_init(&params);
        params.prompt = text.c_str();
        params.negative_prompt = "blurry, distorted, static, text, watermark";
        params.width = width; params.height = height; params.video_frames = count; params.fps = 16;
        params.seed = 42;
        params.sample_params.sample_method = EULER_SAMPLE_METHOD;
        params.sample_params.scheduler = SIMPLE_SCHEDULER;
        params.sample_params.sample_steps = steps;
        params.sample_params.guidance.txt_cfg = 6.0f;
        params.sample_params.flow_shift = 3.0f;
        params.vae_tiling_params.enabled = true;
        params.vae_tiling_params.tile_size_x = 128;
        params.vae_tiling_params.tile_size_y = 128;
        params.vae_tiling_params.temporal_tiling = true;
        int fps = 16;
        if (!generate_video(ctx, &params, &frames, &actual, nullptr, &fps) || !frames || actual == 0)
            throw std::runtime_error(cancelled ? "Generation cancelled" : "Wan generation failed. The device may not have enough memory.");
        if (cancelled) throw std::runtime_error("Generation cancelled");
        update("Encoding MP4 preview...");
        std::ofstream file(dest, std::ios::binary | std::ios::trunc);
        for (int i = 0; i < actual; i++) {
            if (frames[i].width != (uint32_t)width || frames[i].height != (uint32_t)height || frames[i].channel != 3)
                throw std::runtime_error("Unexpected Wan frame format");
            file.write(reinterpret_cast<char*>(frames[i].data), width * height * 3);
        }
        file.close();
        if (!file) throw std::runtime_error("Could not write video frames; check free storage");
        cleanup(); return actual;
    } catch (const std::exception& e) {
        std::string message = e.what();
        { std::lock_guard<std::mutex> lock(state_mutex); if (!last_error.empty()) message += " " + last_error; }
        cleanup();
        env->ThrowNew(env->FindClass("java/io/IOException"), message.c_str()); return 0;
    }
}
