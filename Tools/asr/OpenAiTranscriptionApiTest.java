package tw.nekomimi.nekogram.helpers;

import java.io.*;
import java.nio.file.*;
import okio.Buffer;

public class OpenAiTranscriptionApiTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) throws Exception {
        for (String suffix : new String[]{"", "/", "/chat/completions", "/audio/transcriptions/"}) {
            check(OpenAiTranscriptionApi.endpoint("https://example.org/openai/v1" + suffix, true)
                    .equals("https://example.org/openai/v1/audio/transcriptions"));
            check(OpenAiTranscriptionApi.endpoint("https://example.org/openai/v1" + suffix, false)
                    .equals("https://example.org/openai/v1/chat/completions"));
        }
        check(OpenAiTranscriptionApi.endpoint("https://example.org/v1?route=asr",true)
                .equals("https://example.org/v1/audio/transcriptions?route=asr"));
        File file = File.createTempFile("voice", ".ogg");
        try {
            Files.write(file.toPath(), new byte[]{'O','g','g','S',0,1,2});
            for (boolean video : new boolean[]{false,true}) {
                var body = OpenAiTranscriptionApi.body(file, video, "whisper-1");
                check(body.contentType().toString().startsWith("multipart/form-data; boundary="));
                Buffer buffer = new Buffer(); body.writeTo(buffer);
                String wire = buffer.readUtf8();
                check(wire.contains("name=\"model\"\r\n\r\nwhisper-1"));
                check(wire.contains(video ? "filename=\"audio.m4a\"" : "filename=\"audio.ogg\""));
                check(wire.contains(video ? "Content-Type: audio/mp4" : "Content-Type: audio/ogg"));
                check(wire.contains("OggS\0\1\2"));
                check(!wire.contains("messages") && !wire.contains("input_audio") && !wire.contains("prompt"));
            }
        } finally { file.delete(); }
        check(OpenAiTranscriptionApi.text("{\"text\":\" Привет! \",\"duration\":2}").equals("Привет!"));
        for (String invalid : new String[]{"{}", "null", "not json", "{\"text\":12}", "{\"text\":\"  \"}", "{\"error\":{\"message\":\"bad\"}}"}) {
            try { OpenAiTranscriptionApi.text(invalid); throw new AssertionError(invalid); }
            catch (IOException expected) { checks++; }
        }
        System.out.println("Passed " + checks + " protocol checks");
    }
}
