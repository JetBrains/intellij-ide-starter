package com.intellij.ide.starter.screenRecorder

import com.intellij.ide.starter.runner.IDERunContext
import org.monte.media.Format
import org.monte.media.FormatKeys.MediaType
import org.monte.media.VideoFormatKeys.*
import org.monte.media.math.Rational
import org.monte.screenrecorder.ScreenRecorder
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.io.path.div

class IDEScreenRecorder(runContext: IDERunContext) : ScreenRecorder(
  GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration,
  Rectangle(0, 0, Toolkit.getDefaultToolkit().screenSize.width,
            Toolkit.getDefaultToolkit().screenSize.height),
  Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),
  Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
         CompressorNameKey,
         ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE, DepthKey, 24, FrameRateKey,
         Rational.valueOf(15.0), QualityKey, 1.0f,
         KeyFrameIntervalKey, 15 * 60),
  Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, "black", FrameRateKey,
         Rational.valueOf(30.0)), null,
  (runContext.logsDir / "screenRecording").toFile())