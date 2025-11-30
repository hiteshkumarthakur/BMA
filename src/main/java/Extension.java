import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import ui.MainUI;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("BMA - Burp Mobile Automation");

        montoyaApi.logging().logToOutput("BMA Extension loaded successfully!");

        // Register UI
        MainUI mainUI = new MainUI(montoyaApi);
        montoyaApi.userInterface().registerSuiteTab("BMA", mainUI.getComponent());
    }
}