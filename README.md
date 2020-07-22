[![](https://jitpack.io/v/hannesa2/FloatingView.svg)](https://jitpack.io/#hannesa2/FloatingView)

# FloatingView

You can use instead: https://developer.android.com/guide/topics/ui/bubbles

The Android project is View to display information such as chat in front.
To API Level 16 or higher are supported

## Screenshots
![](./screenshot/animation.gif)  
<img src="./screenshot/ss01.png" width="200">
<img src="./screenshot/ss02.png" width="200">
<img src="./screenshot/ss03.png" width="200">

## How to use
1) Add this to your **build.gradle**.
  ```java
  repositories {
      maven {
          url "https://jitpack.io"
      }
  }

  dependencies {
    implementation 'com.github.hannesa2:FloatingView:$latestVersion'
  }
  ```
  
2) Implement Service for displaying FloatingView
```Kotlin
class SimpleFloatingViewService : Service(), FloatingViewListener {
  ...
}
```
  
3) You will do the setting of the View to be displayed in the FloatingView(Sample have a set in onStartCommand)
```Kotlin
    val inflater = LayoutInflater.from(this)
    val iconView = inflater.inflate(R.layout.widget_mail, null, false) as ImageView
    iconView.setOnClickListener { }
```  

4) Use the FloatingViewManager, make the setting of FloatingView
```Kotlin
    floatingViewManager = FloatingViewManager(this, this).apply {
        setFixedTrashIconImage(R.drawable.ic_trash_fixed)
        setActionTrashIconImage(R.drawable.ic_trash_action)
        setSafeInsetRect(intent.getParcelableExtra<Parcelable>(EXTRA_CUTOUT_SAFE_AREA) as Rect)
    }
```  

The second argument of `FloatingViewManager` is `FloatingViewListener`
  
Describe the process (`onFinishFloatingView`) that is called when you exit the FloatingView
```Kotlin
    override fun onFinishFloatingView() {
        stopSelf()
    }
```
  
5) Add the permission to AndroidManifest
```xml
 <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
 <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```  
  
6) Define the Service to AndroidManifest

```
    <application ...>
        ...
        <service
            android:name="info.hannes.floatingView.sample.service.SimpleFloatingViewService"
            android:exported="false"/>
        ...
    </application>
```
  
7) Describe the process to start the Service (run on foreground)

- FloatingViewControlFragment.kt

```Kotlin
    val intent = Intent(activity, SimpleFloatingViewService::class.java)
    ContextCompat.startForegroundService(activity, intent)
```

- SimpleFloatingViewService.kt

```Kotlin
override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    ...
    startForeground(NOTIFICATION_ID, createNotification(this));
    ...
}
```

8) Create notification channel (targetSdkVersion >= 26)

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    final String channelId = getString(R.string.default_floatingview_channel_id);
    final String channelName = getString(R.string.default_floatingview_channel_name);
    final NotificationChannel defaultChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN);
    final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (manager != null) {
        manager.createNotificationChannel(defaultChannel);
    }
}

```

9) Add DisplayCutout process(API Level >= 28)

Call `FloatingViewManager.findCutoutSafeArea(activity)`.  
Note: Activity must be portrait oriented.  
Note: You must not set `windowLayoutInDisplayCutoutMode` to `never`.  

- FloatingViewControlFragment.kt

```Kotlin
    val intent = Intent(activity, service)
    intent.putExtra(key, FloatingViewManager.findCutoutSafeArea(activity!!))
    ContextCompat.startForegroundService(activity, intent)
```

- CustomFloatingViewService.kt

```Kotlin
    floatingViewManager = FloatingViewManager(this, this).apply {
        ...
        setSafeInsetRect(intent.getParcelableExtra<Parcelable>(EXTRA_CUTOUT_SAFE_AREA) as Rect)
    }
```


## Static Options
It can be set only when displaying for the first time
  
```Kotlin
    val options = loadOptions(metrics)
    floatingViewManager!!.addViewToWindow(iconView, options)
```

|Option|Description|  
|:-:|---|  
|shape|`FloatingViewManager.SHAPE_CIRCLE`:Circle(default)<br> `FloatingViewManager.SHAPE_RECTANGLE`:Rectangle|  
|overMargin|Margin over the edge of the screen (px)<br>(default) 0|  
|floatingViewX|X coordinate of initial display<br>(default) left side of display|  
|floatingViewY|Y coordinate of initial display<br>(default) top of display|  
|floatingViewWidth|FloatingView width<br>(default) The width of the layout added to FloatingView |  
|floatingViewHeight|FloatingView height<br>(default) The height of the layout added to FloatingView|  
|moveDirection|`FloatingViewManager.MOVE_DIRECTION_DEFAULT`:Left end or right end(default)<br> `FloatingViewManager.MOVE_DIRECTION_LEFT`:Left end<br>`FloatingViewManager.MOVE_DIRECTION_RIGHT`:Right end<br>`FloatingViewManager.MOVE_DIRECTION_NONE`:Not move<br>`FloatingViewManager.MOVE_DIRECTION_NEAREST`:Move nearest edge<br>`FloatingViewManager.MOVE_DIRECTION_THROWN`:Move in the throwing direction (left end or right end)|
|usePhysics|Use physics-based animation(depends on `moveDirection`)<br>(default) true<br>Info:If `MOVE_DIRECTION_NEAREST` is set, nothing happens<br>Info:Can not be used before API 16|
|animateInitialMove|If true, animation when first displayed<br>(FloatingViewX, floatingViewY) to screen edge<br>Info: If `MOVE_DIRECTION_NONE` is set, nothing happens|  

## Dynamic Options
It can be set any time  
  
```java
mFloatingViewManager.setFixedTrashIconImage(R.drawable.ic_trash_fixed);
mFloatingViewManager.setActionTrashIconImage(R.drawable.ic_trash_action);
```

|Option|Description|
|:-:|---|
|setFixedTrashIconImage|It is an icon that does *not* enlarge when FloatingView overlaps.|
|setActionTrashIconImage|It is an icon that enlarge when FloatingView overlaps.|
|setDisplayMode|`FloatingViewManager.DISPLAY_MODE_SHOW_ALWAYS`:Always show<br>`FloatingViewManager.DISPLAY_MODE_HIDE_ALWAYS`:Always hidden<br>`FloatingViewManager.DISPLAY_MODE_HIDE_FULLSCREEN`:It is hidden when in full screen|
|setTrashViewEnabled|If false, the trash icon does not show during dragging.<br>(default) true|

# License

    Copyright 2020 hannesa2

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

