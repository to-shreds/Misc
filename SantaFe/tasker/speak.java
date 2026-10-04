// Bounded Android text-to-speech, no speech or text logged.
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
String text = tasker.getVariable("par1");
if (text == null || text.length() == 0) return "no text";
if (text.length() > 500) text = text.substring(0, 500);
CountDownLatch ready = new CountDownLatch(1);
int[] state = new int[]{-1};
TextToSpeech speaker = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
    public void onInit(int status) { state[0] = status; ready.countDown(); }
});
try {
    if (!ready.await(8, TimeUnit.SECONDS) || state[0] != TextToSpeech.SUCCESS) return "speech unavailable";
    if (speaker.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "sf-control") == TextToSpeech.ERROR) return "speech rejected";
    // Wait briefly for synthesis to start, then bounded wait for completion.
    Thread.sleep(600);
    long until = System.currentTimeMillis() + 45000;
    while (speaker.isSpeaking() && System.currentTimeMillis() < until) Thread.sleep(250);
    return "speech finished";
} finally { speaker.stop(); speaker.shutdown(); }
