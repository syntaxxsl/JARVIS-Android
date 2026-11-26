package com.jarvis.assistant.core.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.app.NotificationManager
import android.net.Uri
import android.provider.Settings.System
import android.widget.Toast

/**
 * Executes only Android operations exposed to ordinary applications.
 * Restricted controls open the relevant system screen instead of pretending to change state.
 */
object PhoneActions {
    @Volatile private var torchEnabled = false

    fun run(context: Context, action: String): String {
        val result = try {
            when (action) {
                "torch", "torch_on", "torch_off" -> {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        "El feneri için kamera izni gerekli."
                    } else {
                        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val cameraId = manager.cameraIdList.firstOrNull { id ->
                            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        } ?: return "Bu cihazda kullanılabilir flaş bulunamadı."
                        val nextState = when (action) {
                            "torch_on" -> true
                            "torch_off" -> false
                            else -> !torchEnabled
                        }
                        manager.setTorchMode(cameraId, nextState)
                        torchEnabled = nextState
                        if (nextState) "El feneri açıldı." else "El feneri kapatıldı."
                    }
                }
                "volume_up", "volume_down", "volume_mute" -> {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    when (action) {
                        "volume_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        "volume_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        else -> {
                            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, if (current == 0) audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2 else 0, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    "Ses seviyesi güncellendi."
                }
                "brightness_up", "brightness_down" -> {
                    if (!System.canWrite(context)) {
                        open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        "Parlaklık kontrolü için JARVIS'e sistem ayarlarını değiştirme izni ver."
                    } else {
                        val current = System.getInt(context.contentResolver, System.SCREEN_BRIGHTNESS, 128)
                        val next = (current + if (action == "brightness_up") 32 else -32).coerceIn(10, 255)
                        System.putInt(context.contentResolver, System.SCREEN_BRIGHTNESS, next)
                        "Ekran parlaklığı güncellendi."
                    }
                }
                "timeout_30" , "timeout_120" -> {
                    if (!System.canWrite(context)) {
                        open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        "Ekran zaman aşımı için JARVIS'e sistem ayarlarını değiştirme izni ver."
                    } else {
                        System.putInt(context.contentResolver, System.SCREEN_OFF_TIMEOUT, if (action == "timeout_30") 30_000 else 120_000)
                        "Ekran zaman aşımı güncellendi."
                    }
                }
                "dnd", "dnd_on", "dnd_off" -> {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && manager.isNotificationPolicyAccessGranted) {
                        val next = when (action) {
                            "dnd_on" -> NotificationManager.INTERRUPTION_FILTER_NONE
                            "dnd_off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                            else -> if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE)
                                NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE
                        }
                        manager.setInterruptionFilter(next)
                        if (next == NotificationManager.INTERRUPTION_FILTER_NONE) "Rahatsız Etmeyin etkinleştirildi." else "Rahatsız Etmeyin kapatıldı."
                    } else {
                        open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        "Rahatsız Etmeyin kontrolü için JARVIS'e bildirim politikası erişimi ver."
                    }
                }
                "alarm" -> open(context, Intent(AlarmClock.ACTION_SET_ALARM))
                "wifi" -> open(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                "bluetooth" -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "settings" -> open(context, Intent(Settings.ACTION_SETTINGS))
                "sound" -> open(context, Intent(Settings.ACTION_SOUND_SETTINGS))
                "display" -> open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
                "apps" -> open(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))
                "notifications" -> open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                "permissions" -> open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                "location" -> open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                "battery" -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                "dnd_access" -> open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                "write_settings" -> open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                else -> "Bu telefon komutu henüz desteklenmiyor."
            }
        } catch (error: Exception) {
            "İşlem gerçekleştirilemedi: ${error.message ?: "Android kısıtlaması"}"
        }
        PhoneActionHistory.record(context, action, result)
        return result
    }

    private fun open(context: Context, intent: Intent): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "İlgili Android kontrolü açıldı."
    }
}
