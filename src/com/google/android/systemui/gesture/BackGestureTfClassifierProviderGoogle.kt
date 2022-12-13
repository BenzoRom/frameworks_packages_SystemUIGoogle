/*
 * Copyright (C) 2022 Benzo Rom
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
package com.google.android.systemui.gesture

import android.content.res.AssetManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel
import javax.inject.Inject

class BackGestureTfClassifierProviderGoogle constructor(
    am: AssetManager,
    modelName: String
) : BackGestureTfClassifierProvider() {
    private val outputMap = hashMapOf<Int, Any>()
    private val vocabFile = "$modelName.vocab"
    private var interpreter = am.openFd("$modelName.tflite")
    override fun isActive(): Boolean = true
    override fun release() = interpreter.close()
    
    override fun loadVocab(am: AssetManager): Map<String, Int> {
        return am.open(vocabFile).use { input ->
            String(input.readBytes()).lines().asSequence()
                .withIndex().map { it.value to it.index }.toMap()
        }
    }

    override fun predict(featuresVector: Array<Any>): Float {
        val output = floatArrayOf(0f).also { outputMap[0] = it }
        with(interpreter) {
            use {
                Interpreter(it.createInputStream().channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    it.startOffset, it.declaredLength
                ))
            }.runForMultipleInputsOutputs(featuresVector, outputMap)
        }
        return output[0]
    }
}
