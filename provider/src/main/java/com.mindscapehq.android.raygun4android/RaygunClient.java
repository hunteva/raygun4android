package main.java.com.mindscapehq.android.raygun4android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import java.io.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.gson.Gson;

import main.java.com.mindscapehq.android.raygun4android.messages.RaygunMessage;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunPulseData;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunPulseDataMessage;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunPulseMessage;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunPulseTimingMessage;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunUserContext;
import main.java.com.mindscapehq.android.raygun4android.messages.RaygunUserInfo;
import main.java.com.mindscapehq.android.raygun4android.network.RaygunNetworkUtils;
import main.java.com.mindscapehq.android.raygun4android.services.CrashReportingPostService;
import main.java.com.mindscapehq.android.raygun4android.services.RUMPostService;
import main.java.com.mindscapehq.android.raygun4android.utils.RaygunFileUtils;
import main.java.com.mindscapehq.android.raygun4android.utils.RaygunUtils;
import main.java.com.mindscapehq.android.raygun4android.utils.RaygunFileFilter;

/**
 * User: Mindscape
 * The official Raygun provider for Android. This is the main class that provides functionality
 * for automatically sending exceptions to the Raygun service.
 *
 * You should call Init() on the static RaygunClient instance, passing in the current Context,
 * instead of instantiating this class.
 */
public class RaygunClient {
  private static String apiKey;
  private static Context context;
  private static String version;
  private static String appContextIdentifier;
  private static String user;
  private static RaygunUserInfo userInfo;
  private static RaygunUncaughtExceptionHandler handler;
  private static RaygunOnBeforeSend onBeforeSend;
  private static List tags;
  private static Map userCustomData;
  private static String sessionId;

  // region # Initialisation
  //---------------------------------------------------------------------------------------

  /**
   * Initializes the Raygun client. This expects that you have placed the API key in your
   * AndroidManifest.xml, in a meta-data element.
   * @param context The context of the calling Android activity.
   */
  public static void init(Context context) {
    String apiKey = readApiKey(context);
    init(context, apiKey);
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #init(Context)}
   */
  @Deprecated public static void Init(Context context) {
    init(context);
  }

  /**
   * Initializes the Raygun client with the version of your application.
   * This expects that you have placed the API key in your AndroidManifest.xml, in a meta-data element.
   * @param version The version of your application, format x.x.x.x, where x is a positive integer.
   * @param context The context of the calling Android activity.
   */
  public static void init(String version, Context context) {
    String apiKey = readApiKey(context);
    init(context, apiKey, version);
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #init(String,Context)}
   */
  @Deprecated public static void Init(String version, Context context) {
    init(version, context);
  }

  /**
   * Initializes the Raygun client with your Android application's context and your
   * Raygun API key. The version transmitted will be the value of the versionName attribute in your manifest element.
   * @param context The Android context of your activity
   * @param apiKey An API key that belongs to a Raygun application created in your dashboard
   */
  public static void init(Context context, String apiKey) {
    RaygunClient.apiKey = apiKey;
    RaygunClient.context = context;
    RaygunClient.appContextIdentifier = UUID.randomUUID().toString();

    RaygunLogger.d("Configuring Raygun (v"+RaygunSettings.RAYGUN_CLIENT_VERSION+")");

    try {
      RaygunClient.version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException e) {
      RaygunClient.version = "Not provided";
      RaygunLogger.w("Couldn't read application version from calling package");
    }
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #init(Context,String)}
   */
  @Deprecated public static void Init(Context context, String apiKey) {
    init(context, apiKey);
  }

  /**
   * Initializes the Raygun client with your Android application's context, your
   * Raygun API key, and the version of your application
   * @param context The Android context of your activity
   * @param apiKey An API key that belongs to a Raygun application created in your dashboard
   * @param version The version of your application, format x.x.x.x, where x is a positive integer.
   */
  public static void init(Context context, String apiKey, String version) {
    init(context, apiKey);
    RaygunClient.version = version;
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #init(Context,String,String)}
   */
  @Deprecated public static void Init(Context context, String apiKey, String version) {
    init(context, apiKey, version);
  }

  private static String readApiKey(Context context)
  {
    try {
      ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      Bundle bundle = ai.metaData;
      return bundle.getString(RaygunSettings.APIKEY_MANIFEST_FIELD);
    } catch (PackageManager.NameNotFoundException e) {
      RaygunLogger.e("Couldn't read API key from your AndroidManifest.xml <meta-data /> element; cannot send: " + e.getMessage());
    }
    return null;
  }

  //---------------------------------------------------------------------------------------
  // endregion

  // region # Getters/Setters
  //---------------------------------------------------------------------------------------

  public static String getApiKey() {
    return RaygunClient.apiKey;
  }

  /**
   * Manually stores the version of your application to be transmitted with each message, for version
   * filtering. This is normally read from your AndroidManifest.xml (the versionName attribute on manifest element)
   * or passed in on init(); this is only provided as a convenience.
   * @param version The version of your application, format x.x.x.x, where x is a positive integer.
   */
  public static void setVersion(String version) {
    if (version != null) {
      RaygunClient.version = version;
    }
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #setVersion(String)}
   */
  @Deprecated public static void SetVersion(String version) {
    setVersion(version);
  }

  public static RaygunUncaughtExceptionHandler getExceptionHandler() { return RaygunClient.handler; }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #getExceptionHandler()}
   */
  @Deprecated public static RaygunUncaughtExceptionHandler GetExceptionHandler() { return getExceptionHandler(); }

  public static List getTags() {
    return RaygunClient.tags;
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #getTags()}
   */
  @Deprecated public static List GetTags() {
    return getTags();
  }

  public static void setTags(List tags) {
    RaygunClient.tags = tags;
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #setTags(List)}
   */
  @Deprecated public static void SetTags(List tags) {
    setTags(tags);
  }

  public static Map getUserCustomData() {
    return RaygunClient.userCustomData;
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #getUserCustomData()}
   */
  @Deprecated public static Map GetUserCustomData() {
    return getUserCustomData();
  }

  public static void setUserCustomData(Map userCustomData) { RaygunClient.userCustomData = userCustomData; }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #setUserCustomData(Map)}
   */
  @Deprecated public static void SetUserCustomData(Map userCustomData) {  setUserCustomData(userCustomData); }

  public static void setOnBeforeSend(RaygunOnBeforeSend onBeforeSend) { RaygunClient.onBeforeSend = onBeforeSend; }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #setOnBeforeSend(RaygunOnBeforeSend)}
   */
  @Deprecated public static void SetOnBeforeSend(RaygunOnBeforeSend onBeforeSend) { setOnBeforeSend(onBeforeSend); }

  //---------------------------------------------------------------------------------------
  // endregion

  // region # Attach Products
  //---------------------------------------------------------------------------------------

  /**
   * Attaches a pre-built Raygun exception handler to the thread's DefaultUncaughtExceptionHandler.
   * This automatically sends any exceptions that reaches it to the Raygun API.
   */
  public static void attachExceptionHandler() {
    UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
    if (!(oldHandler instanceof RaygunUncaughtExceptionHandler)) {
      RaygunClient.handler = new RaygunUncaughtExceptionHandler(oldHandler);
      Thread.setDefaultUncaughtExceptionHandler(RaygunClient.handler);
    }
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #attachExceptionHandler()}
   */
  @Deprecated public static void AttachExceptionHandler() {
    attachExceptionHandler();
  }

  /**
   * Attaches a pre-built Raygun exception handler to the thread's DefaultUncaughtExceptionHandler.
   * This automatically sends any exceptions that reaches it to the Raygun API.
   * @param tags A list of tags that relate to the calling application's currently build or state.
   *             These will be appended to all exception messages sent to Raygun.
   * @deprecated Call attachExceptionHandler(), then setTags(List) instead
   */
  @Deprecated public static void AttachExceptionHandler(List tags) {
    UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
    if (!(oldHandler instanceof RaygunUncaughtExceptionHandler)) {
      RaygunClient.handler = new RaygunUncaughtExceptionHandler(oldHandler, tags);
      Thread.setDefaultUncaughtExceptionHandler(RaygunClient.handler);
    }
  }

  /**
   * Attaches the Raygun Pulse feature which will automatically report session and view events.
   * @param activity The main/entry activity of the Android app.
   */
  public static void attachPulse(Activity activity) {
    Pulse.attach(activity);
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #attachPulse(Activity)}
   */
  @Deprecated public static void AttachPulse(Activity activity) {
    attachPulse(activity);
  }

  /**
   * Attaches the Raygun Pulse feature which will automatically report session and view events.
   * @param activity The main/entry activity of the Android app.
   * @param networkLogging Automatically report the performance of network requests.
   */
  public static void attachPulse(Activity activity, boolean networkLogging) {
    Pulse.attach(activity, networkLogging);
  }

  /**
   * Attaches a pre-built Raygun exception handler to the thread's DefaultUncaughtExceptionHandler.
   * This automatically sends any exceptions that reaches it to the Raygun API.
   * @param tags A list of tags that relate to the calling application's currently build or state.
   *             These will be appended to all exception messages sent to Raygun.
   * @param userCustomData A set of key-value pairs that will be attached to each exception message
   *                       sent to Raygun. This can contain any extra data relating to the calling
   *                       application's state you would like to see.
   * @deprecated Call attachExceptionHandler(), then setUserCustomData(Map) instead
   */
  @Deprecated public static void AttachExceptionHandler(List tags, Map userCustomData) {
    UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
    if (!(oldHandler instanceof RaygunUncaughtExceptionHandler)) {
      RaygunClient.handler = new RaygunUncaughtExceptionHandler(oldHandler, tags, userCustomData);
      Thread.setDefaultUncaughtExceptionHandler(RaygunClient.handler);
    }
  }

  //---------------------------------------------------------------------------------------
  // endregion

  // region # Send Exceptions
  //---------------------------------------------------------------------------------------

  /**
   * Sends an exception-type object to Raygun.
   * @param throwable The Throwable object that occurred in your application that will be sent to Raygun.
   */
  public static void send(Throwable throwable) {
    send(throwable, null, null);
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #send(Throwable)}
   */
  @Deprecated public static void Send(Throwable throwable) {
    send(throwable);
  }

  /**
   * Sends an exception-type object to Raygun with a list of tags you specify.
   * @param throwable The Throwable object that occurred in your application that will be sent to Raygun.
   * @param tags A list of data that will be attached to the Raygun message and visible on the error in the dashboard.
   *             This could be a build tag, lifecycle state, debug/production version etc.
   */
  public static void send(Throwable throwable, List tags) {
    send(throwable, tags, null);
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #send(Throwable,List)}
   */
  @Deprecated public static void Send(Throwable throwable, List tags) {
    send(throwable, tags);
  }

  /**
   * Sends an exception-type object to Raygun with a list of tags you specify, and a set of
   * custom data.
   * @param throwable The Throwable object that occurred in your application that will be sent to Raygun.
   * @param tags A list of data that will be attached to the Raygun message and visible on the error in the dashboard.
   *             This could be a build tag, lifecycle state, debug/production version etc.
   * @param userCustomData A set of custom key-value pairs relating to your application and its current state. This is a bucket
   *                       where you can attach any related data you want to see to the error.
   */
  public static void send(Throwable throwable, List tags, Map userCustomData) {
    RaygunMessage msg = buildMessage(throwable);

    if (msg == null) {
      RaygunLogger.e("Failed to send RaygunMessage - due to invalid message being built");
      return;
    }

    msg.getDetails().setTags(RaygunUtils.mergeLists(RaygunClient.tags, tags));
    msg.getDetails().setUserCustomData(RaygunUtils.mergeMaps(RaygunClient.userCustomData, userCustomData));

    if (RaygunClient.onBeforeSend != null) {
      msg = RaygunClient.onBeforeSend.onBeforeSend(msg);
      if (msg == null) {
        return;
      }
    }

    enqueueWorkForCrashReportingService(RaygunClient.apiKey, new Gson().toJson(msg));
    postCachedMessages();
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #send(Throwable,List,Map)}
   */
  @Deprecated public static void Send(Throwable throwable, List tags, Map userCustomData) {
    send(throwable, tags, userCustomData);
  }

  private static RaygunMessage buildMessage(Throwable throwable) {
    try {
      RaygunMessage msg =  RaygunMessageBuilder.instance()
          .setEnvironmentDetails(RaygunClient.context)
          .setMachineName(Build.MODEL)
          .setExceptionDetails(throwable)
          .setClientDetails()
          .setAppContext(RaygunClient.appContextIdentifier)
          .setVersion(RaygunClient.version)
          .setNetworkInfo(RaygunClient.context)
          .build();

      if (RaygunClient.version != null) {
        msg.getDetails().setVersion(RaygunClient.version);
      }

      if (RaygunClient.userInfo != null) {
        msg.getDetails().setUserContext(RaygunClient.userInfo, RaygunClient.context);
      }
      else if (RaygunClient.user != null) {
        msg.getDetails().setUserContext(RaygunClient.user);
      }
      else {
        msg.getDetails().setUserContext(RaygunClient.context);
      }
      return msg;
    }
    catch (Exception e) {
      RaygunLogger.e("Failed to build RaygunMessage - " + e);
    }
    return null;
  }

  private static void enqueueWorkForCrashReportingService(String apiKey, String jsonPayload) {
    Intent intent = new Intent(RaygunClient.context, CrashReportingPostService.class);
    intent.setAction("main.java.com.mindscapehq.android.raygun4android.intent.action.LAUNCH_CRASHREPORTING_POST_SERVICE");
    intent.setPackage("main.java.com.mindscapehq.android.raygun4android");
    intent.setComponent(new ComponentName(RaygunClient.context, CrashReportingPostService.class));

    intent.putExtra("msg", jsonPayload);
    intent.putExtra("apikey", apiKey);

    CrashReportingPostService.enqueueWork(RaygunClient.context, intent);
  }

  //---------------------------------------------------------------------------------------
  // endregion

  // region # RUM Methods
  //---------------------------------------------------------------------------------------

  protected static void sendPulseEvent(String name) {
    if (RaygunSettings.RUM_EVENT_SESSION_START.equals(name)) {
      RaygunClient.sessionId = UUID.randomUUID().toString();
    }

    RaygunPulseMessage message = new RaygunPulseMessage();
    RaygunPulseDataMessage pulseData = new RaygunPulseDataMessage();

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    Calendar c = Calendar.getInstance();

    if (RaygunSettings.RUM_EVENT_SESSION_END.equals(name)) {
      c.add(Calendar.SECOND, 2);
    }

    String timestamp = df.format(c.getTime());
    pulseData.setTimestamp(timestamp);
    pulseData.setVersion(RaygunClient.version);
    pulseData.setOS("Android");
    pulseData.setOSVersion(Build.VERSION.RELEASE);
    pulseData.setPlatform(String.format("%s %s", Build.MANUFACTURER, Build.MODEL));

    RaygunUserContext userContext;

    if (RaygunClient.userInfo == null) {
      userContext = new RaygunUserContext(new RaygunUserInfo(null, null, null, null, null, true), RaygunClient.context);
    } else {
      userContext = new RaygunUserContext(RaygunClient.userInfo, RaygunClient.context);
    }

    pulseData.setUser(userContext);

    pulseData.setSessionId(RaygunClient.sessionId);
    pulseData.setType(name);

    message.setEventData(new RaygunPulseDataMessage[]{ pulseData });

    enqueueWorkForRUMService(RaygunClient.apiKey, new Gson().toJson(message));
  }

  /**
   * Sends a pulse timing event to Raygun. The message is sent on a background thread.
   * @param eventType The type of event that occurred.
   * @param name The name of the event resource such as the activity name or URL of a network call.
   * @param milliseconds The duration of the event in milliseconds.
   */
  public static void sendPulseTimingEvent(RaygunPulseEventType eventType, String name, long milliseconds) {
    if (RaygunClient.sessionId == null) {
      sendPulseEvent(RaygunSettings.RUM_EVENT_SESSION_START);
    }

    if (eventType == RaygunPulseEventType.ACTIVITY_LOADED) {
      if (RaygunClient.shouldIgnoreView(name)) {
        return;
      }
    }

    RaygunPulseMessage message = new RaygunPulseMessage();
    RaygunPulseDataMessage dataMessage = new RaygunPulseDataMessage();

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    Calendar c = Calendar.getInstance();
    c.add(Calendar.MILLISECOND, -(int)milliseconds);
    String timestamp = df.format(c.getTime());

    dataMessage.setTimestamp(timestamp);
    dataMessage.setSessionId(RaygunClient.sessionId);
    dataMessage.setVersion(RaygunClient.version);
    dataMessage.setOS("Android");
    dataMessage.setOSVersion(Build.VERSION.RELEASE);
    dataMessage.setPlatform(String.format("%s %s", Build.MANUFACTURER, Build.MODEL));
    dataMessage.setType("mobile_event_timing");

    RaygunUserContext userContext;

    if (RaygunClient.userInfo == null) {
      userContext = new RaygunUserContext(new RaygunUserInfo(null, null, null, null, null, true), RaygunClient.context);
    } else {
      userContext = new RaygunUserContext(RaygunClient.userInfo, RaygunClient.context);
    }

    dataMessage.setUser(userContext);

    RaygunPulseData data = new RaygunPulseData();
    RaygunPulseTimingMessage timingMessage = new RaygunPulseTimingMessage();
    timingMessage.setType(eventType == RaygunPulseEventType.ACTIVITY_LOADED ? "p" : "n");
    timingMessage.setDuration(milliseconds);
    data.setName(name);
    data.setTiming(timingMessage);

    RaygunPulseData[] dataArray = new RaygunPulseData[]{ data };
    String dataStr = new Gson().toJson(dataArray);
    dataMessage.setData(dataStr);

    message.setEventData(new RaygunPulseDataMessage[]{ dataMessage });

    enqueueWorkForRUMService(RaygunClient.apiKey, new Gson().toJson(message));
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #sendPulseTimingEvent(RaygunPulseEventType,String,long)}
   */
  @Deprecated public static void SendPulseTimingEvent(RaygunPulseEventType eventType, String name, long milliseconds) {
    sendPulseTimingEvent(eventType, name, milliseconds);
  }

  private static void enqueueWorkForRUMService(String apiKey, String jsonPayload) {
    Intent intent = new Intent(RaygunClient.context, RUMPostService.class);
    intent.setAction("main.java.com.mindscapehq.android.raygun4android.intent.action.LAUNCH_RUM_POST_SERVICE");
    intent.setPackage("main.java.com.mindscapehq.android.raygun4android");
    intent.setComponent(new ComponentName(RaygunClient.context, RUMPostService.class));

    intent.putExtra("msg", jsonPayload);
    intent.putExtra("apikey", apiKey);

    RUMPostService.enqueueWork(RaygunClient.context, intent);
  }

  /**
   * Allows the user to add more URLs to filter out, so network timing events are not sent for them.
   * @param urls An array of urls to filter out by.
   */
  public static void ignoreURLs(String[] urls) {
    RaygunSettings.ignoreURLs(urls);
  }

  /**
   * Allows the user to add more views to filter out, so load timing events are not sent for them.
   * @param views An array of activity names to filter out by.
   */
  public static void ignoreViews(String[] views) {
    RaygunSettings.ignoreViews(views);
  }

  private static boolean shouldIgnoreView(String viewName) {
    if (viewName == null) {
      return true;
    }

    for (String ignoredView : RaygunSettings.getIgnoredViews()) {
      if (viewName.contains(ignoredView) || ignoredView.contains(viewName)) {
        return true;
      }
    }

    return false;
  }

  //---------------------------------------------------------------------------------------
  // endregion

  private static Boolean validateApiKey(String apiKey) throws Exception {
    if (apiKey.length() == 0) {
      RaygunLogger.e("API key has not been provided, exception will not be logged");
      return false;
    }
    else {
      return true;
    }
  }

  private static void postCachedMessages() {
    if (RaygunNetworkUtils.hasInternetConnection(RaygunClient.context)) {
      File[] fileList = RaygunClient.context.getCacheDir().listFiles(new RaygunFileFilter());
      for (File f : fileList) {
        try {
          if (RaygunFileUtils.getExtension(f.getName()).equalsIgnoreCase(RaygunSettings.DEFAULT_FILE_EXTENSION)) {
            ObjectInputStream ois = null;
            try {
              ois = new ObjectInputStream(new FileInputStream(f));
              SerializedMessage serializedMessage = (SerializedMessage) ois.readObject();
              enqueueWorkForCrashReportingService(RaygunClient.apiKey, serializedMessage.message);
              f.delete();
            } finally {
              if (ois != null) {
                ois.close();
              }
            }
          }
        } catch (FileNotFoundException e) {
          RaygunLogger.e("Error loading cached message from filesystem - " + e.getMessage());
        } catch (IOException e) {
          RaygunLogger.e("Error reading cached message from filesystem - " + e.getMessage());
        } catch (ClassNotFoundException e) {
          RaygunLogger.e("Error in cached message from filesystem - " + e.getMessage());
        }
      }
    }
  }

  // region # Post Methods
  //---------------------------------------------------------------------------------------

  /**
   * Raw post method that delivers a pre-built RaygunMessage to the Raygun API. You do not need to call this method
   * directly unless you want to manually build your own message - for most purposes you should call Send().
   * @param apiKey The API key of the app to deliver to
   * @param jsonPayload The JSON representation of a RaygunMessage to be delivered over HTTPS.
   * @return HTTP result code - 202 if successful, 403 if API key invalid, 400 if bad message (invalid properties)
   */
  public static int post(String apiKey, String jsonPayload) {
    try {
      if (validateApiKey(apiKey)) {
        URL endpoint = new URL(RaygunSettings.getCrashReportingEndpoint());
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();

        try {
          connection.setRequestMethod("POST");
          connection.setRequestProperty("X-ApiKey", apiKey);
          connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

          OutputStream outputStream = connection.getOutputStream();
          outputStream.write(jsonPayload.toString().getBytes("UTF-8"));
          outputStream.close();

          int responseCode = connection.getResponseCode();
          RaygunLogger.d("Exception message HTTP POST result: " + responseCode);

          return responseCode;
        }
        finally {
          if (connection != null) {
            connection.disconnect();
          }
        }
      }
    }
    catch (Exception e) {
      RaygunLogger.e("Couldn't post exception - " + e.getMessage());
      e.printStackTrace();
    }
    return -1;
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #post(String,String)}
   */
  @Deprecated public static int Post(String apiKey, String jsonPayload) {
    return post(apiKey, jsonPayload);
  }

  //---------------------------------------------------------------------------------------
  // endregion

  // region # Unique User Tracking
  //---------------------------------------------------------------------------------------

  /**
   * Sets the current user of your application. If user is an email address which is associated with a Gravatar,
   * their picture will be displayed in the error view. If this is not called a random ID will be assigned.
   * If the user context changes in your application (i.e log in/out), be sure to call this again with the
   * updated user name/email address.
   * @param user A user name or email address representing the current user
   */
  public static void setUser(String user) {
    if (user != null && user.length() > 0) {
      RaygunClient.user = user;
    }
  }

  /**
   * @deprecated As of release 3.0.0, replaced by {@link #setUser(RaygunUserInfo)}
   */
  @Deprecated public static void SetUser(RaygunUserInfo userInfo) {
    setUser(userInfo);
  }

  public static void setUser(RaygunUserInfo userInfo) {
    RaygunClient.userInfo = userInfo;
  }

  //---------------------------------------------------------------------------------------
  // endregion

  public static class RaygunUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private UncaughtExceptionHandler defaultHandler;
    private List tags;
    private Map userCustomData;

    public RaygunUncaughtExceptionHandler(UncaughtExceptionHandler defaultHandler) {
      this.defaultHandler = defaultHandler;
    }

    @Deprecated public RaygunUncaughtExceptionHandler(UncaughtExceptionHandler defaultHandler, List tags) {
      this.defaultHandler = defaultHandler;
      this.tags = tags;
    }

    @Deprecated public RaygunUncaughtExceptionHandler(UncaughtExceptionHandler defaultHandler, List tags, Map userCustomData) {
      this.defaultHandler = defaultHandler;
      this.tags = tags;
      this.userCustomData = userCustomData;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
      if (userCustomData != null) {
        RaygunClient.send(throwable, tags, userCustomData);
      }
      else if (tags != null) {
        RaygunClient.send(throwable, tags);
      }
      else {
        List tags = new ArrayList();
        tags.add("UnhandledException");
        RaygunClient.send(throwable, tags);
        Pulse.sendRemainingActivity();
      }
      defaultHandler.uncaughtException(thread, throwable);
    }
  }
}
