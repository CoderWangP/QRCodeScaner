/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wp.qrcode

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Vibrator
import java.io.Closeable
import java.io.IOException

internal class BeepManager(private val activity: Activity?) : MediaPlayer.OnErrorListener, Closeable {
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "BeepManager"
        private const val BEEP_VOLUME = 0.10f
        private const val VIBRATE_DURATION = 200L
    }

    init {
        openMediaPlayer()
    }

    @Synchronized
    fun openMediaPlayer() {
        // The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
        // so we now play on the music stream.
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        mediaPlayer = buildMediaPlayer(activity)
    }

    @Synchronized
    fun playBeepSoundAndVibrate() {
        mediaPlayer?.start()
        val vibrator = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibrator?.vibrate(VIBRATE_DURATION)
    }

    private fun buildMediaPlayer(activity: Context?): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        try {
            activity?.resources?.openRawResourceFd(R.raw.beep).use { file ->
                mediaPlayer.setDataSource(file?.fileDescriptor, file?.startOffset ?: 0, file?.length
                        ?: 0)
                mediaPlayer.setOnErrorListener(this)
                mediaPlayer.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                mediaPlayer.isLooping = false
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME)
                mediaPlayer.prepare()
                return mediaPlayer
            }
        } catch (ioe: IOException) {
            Logger.w(TAG, ioe)
            mediaPlayer.release()
            return null
        }
    }

    @Synchronized
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            // we are finished, so put up an appropriate error toast if required and finish
            activity?.finish()
        } else {
            // possibly media player error, so release and recreate
            close()
            openMediaPlayer()
        }
        return true
    }

    @Synchronized
    override fun close() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}