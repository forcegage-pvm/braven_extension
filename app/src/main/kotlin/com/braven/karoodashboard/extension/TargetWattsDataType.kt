package com.braven.karoodashboard.extension

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Custom data type that shows the current FTMS trainer target watts
 * on the Karoo ride screen. The rider can add this as a data field
 * on any ride page to see the ERG mode target power.
 *
 * Streams at 1 Hz, reading from FtmsController.status.targetPower
 * via the BravenDashboardExtension singleton.
 */
class TargetWattsDataType(
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "target-watts"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("TargetWattsDataType: startStream")
        val job = CoroutineScope(Dispatchers.IO).launch {
            // Poll the trainer target power at 1 Hz
            while (true) {
                val targetPower = BravenDashboardExtension.instance
                    ?.ftmsController
                    ?.status
                    ?.value
                    ?.targetPower

                if (targetPower != null) {
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(
                                    DataType.Field.SINGLE to targetPower.toDouble(),
                                ),
                            ),
                        ),
                    )
                } else {
                    // No target set — show 0 or searching state
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(
                                    DataType.Field.SINGLE to 0.0,
                                ),
                            ),
                        ),
                    )
                }

                delay(1000)
            }
        }

        emitter.setCancellable {
            Timber.d("TargetWattsDataType: stopStream")
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Use standard numeric rendering, formatted like power (integer watts)
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.POWER))
    }
}
