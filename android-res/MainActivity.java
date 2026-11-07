package app.lomo.reader;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LomoSafPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
