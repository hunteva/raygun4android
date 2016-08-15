package main.java.com.mindscapehq.android.raygun4android.messages;

public class RaygunClientMessage {

  private String version;
  private String clientUrl;
  private String name;

  public RaygunClientMessage()
  {
    setName("Raygun4Android");
    setVersion("2.2.0-beta-1");
    setClientUrl("https://github.com/MindscapeHQ/raygun4android");
  }

  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }
  public String getClientUrl() {
    return clientUrl;
  }
  public void setClientUrl(String clientUrlString) {
    this.clientUrl = clientUrlString;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

}
