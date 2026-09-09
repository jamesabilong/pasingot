import androidx.datastore.preferences.PreferencesProto;
import java.nio.file.*;
public class WatchFixture {
 public static void main(String[] args) throws Exception {
  String json = "{\"schemaVersion\":5,\"entries\":[{\"id\":\"emulator-validation\",\"date\":\"2026-09-09\",\"label\":\"Emulator test\",\"schemaVersion\":5,\"exercises\":[{\"exercise\":\"Test push-ups\",\"reps\":\"8\",\"sets\":2,\"rest\":90},{\"exercise\":\"Test squats\",\"reps\":\"10\",\"sets\":1,\"rest\":0}]}]}";
  var value = PreferencesProto.Value.newBuilder().setString(json).build();
  var prefs = PreferencesProto.PreferenceMap.newBuilder().putPreferences("workout_store_json",value).build();
  Files.write(Path.of(args[0]), prefs.toByteArray());
 }
}
