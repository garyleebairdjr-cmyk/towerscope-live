package io.towerscope.platform

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.towerscope.model.CellIdentity
import io.towerscope.model.CellObservation
import io.towerscope.model.ConnectionRole
import io.towerscope.model.NetworkStatus
import io.towerscope.model.RadioCapabilities
import io.towerscope.model.RadioSnapshot
import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import io.towerscope.model.Transport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Reads serving and neighbor cells from [TelephonyManager]. */
internal class AndroidRadio(private val context: Context) {
    private val tm = context.getSystemService(TelephonyManager::class.java)
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)

    @Volatile private var displayInfo: TelephonyDisplayInfo? = null
    @Volatile private var signalStrength: SignalStrength? = null

    @SuppressLint("MissingPermission")
    fun snapshots(intervalMs: Long): Flow<RadioSnapshot> = callbackFlow {
        var callback: TelephonyCallback? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener, TelephonyCallback.SignalStrengthsListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) { displayInfo = info }
                override fun onSignalStrengthsChanged(ss: SignalStrength) { signalStrength = ss }
            }
            try {
                tm.registerTelephonyCallback(executor, cb)
                callback = cb
            } catch (_: SecurityException) {
                // DisplayInfo needs READ_PHONE_STATE on some builds; continue without it.
            }
        }

        val loop = launch {
            while (isActive) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tm.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cells: MutableList<CellInfo>) {
                                trySend(build(cells))
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                trySend(build(safeAllCellInfo()))
                            }
                        })
                    } else {
                        trySend(build(safeAllCellInfo()))
                    }
                } catch (e: SecurityException) {
                    close(e)
                }
                delay(intervalMs)
            }
        }

        awaitClose {
            loop.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) callback?.let { tm.unregisterTelephonyCallback(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeAllCellInfo(): List<CellInfo> = try { tm.allCellInfo.orEmpty() } catch (_: SecurityException) { emptyList() }

    private fun build(cells: List<CellInfo>): RadioSnapshot {
        val now = System.currentTimeMillis()
        val ss = signalStrength ?: runCatching { tm.signalStrength }.getOrNull()
        val net = networkStatus()
        val observations = cells.mapNotNull { mapCell(it, now) }.toMutableList()

        // 5G NSA: the NR leg is often missing from getAllCellInfo but present in SignalStrength.
        if (net.isNrNsa && observations.none { it.identity.radio == RadioType.NR }) {
            ss?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull()?.let { nr ->
                observations += CellObservation(
                    identity = CellIdentity(RadioType.NR, operatorName = net.operatorName),
                    signal = nrSignal(nr),
                    role = ConnectionRole.SECONDARY,
                    timestampMs = now,
                    synthetic = true,
                )
            }
        }
        // Some modems report no registered cell; promote the strongest one so the UI has a focus.
        if (observations.none { it.role == ConnectionRole.PRIMARY } && observations.isNotEmpty() && cells.any { it.isRegistered }) {
            val i = observations.indexOfFirst { it.role != ConnectionRole.NEIGHBOR }
            if (i >= 0) observations[i] = observations[i].copy(role = ConnectionRole.PRIMARY)
        }
        return RadioSnapshot(
            cells = observations.sortedBy { it.role.ordinal },
            network = net,
            timestampMs = now,
            capabilities = RadioCapabilities(canReadCells = true),
        )
    }

    private fun Int.valid(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MIN_VALUE }
    private fun Long.valid(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE && it >= 0 }

    private fun role(ci: CellInfo): ConnectionRole = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ci.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING -> ConnectionRole.PRIMARY
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ci.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING -> ConnectionRole.SECONDARY
        ci.isRegistered -> ConnectionRole.PRIMARY
        else -> ConnectionRole.NEIGHBOR
    }

    private fun opName(a: CharSequence?, b: CharSequence?): String? =
        (a?.toString()?.takeIf { it.isNotBlank() } ?: b?.toString()?.takeIf { it.isNotBlank() })

    private fun mapCell(ci: CellInfo, now: Long): CellObservation? {
        val r = role(ci)
        return when (ci) {
            is CellInfoLte -> {
                val id = ci.cellIdentity
                val s = ci.cellSignalStrength
                CellObservation(
                    CellIdentity(
                        radio = RadioType.LTE,
                        mcc = id.mccString?.toIntOrNull(),
                        mnc = id.mncString?.toIntOrNull(),
                        area = id.tac.valid()?.toLong(),
                        cellId = id.ci.valid()?.toLong(),
                        pci = id.pci.valid(),
                        channel = id.earfcn.valid(),
                        bandwidthKhz = id.bandwidth.valid()?.takeIf { it > 0 },
                        bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                    ),
                    SignalMetrics(
                        dbm = s.dbm.valid(),
                        level = s.level,
                        asu = s.asuLevel.takeIf { it in 0..97 },
                        rssi = s.rssi.valid(),
                        rsrp = s.rsrp.valid(),
                        rsrq = s.rsrq.valid(),
                        sinr = s.rssnr.valid()?.toDouble(),
                        cqi = s.cqi.valid(),
                        timingAdvance = s.timingAdvance.valid(),
                    ),
                    r, now,
                )
            }
            is CellInfoNr -> {
                val id = ci.cellIdentity as CellIdentityNr
                val s = ci.cellSignalStrength as CellSignalStrengthNr
                CellObservation(
                    CellIdentity(
                        radio = RadioType.NR,
                        mcc = id.mccString?.toIntOrNull(),
                        mnc = id.mncString?.toIntOrNull(),
                        area = id.tac.valid()?.toLong(),
                        cellId = id.nci.valid(),
                        pci = id.pci.valid(),
                        channel = id.nrarfcn.valid(),
                        bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                    ),
                    nrSignal(s),
                    r, now,
                )
            }
            is CellInfoWcdma -> {
                val id = ci.cellIdentity
                val s = ci.cellSignalStrength
                CellObservation(
                    CellIdentity(
                        radio = RadioType.WCDMA,
                        mcc = id.mccString?.toIntOrNull(),
                        mnc = id.mncString?.toIntOrNull(),
                        area = id.lac.valid()?.toLong(),
                        cellId = id.cid.valid()?.toLong(),
                        pci = id.psc.valid(),
                        channel = id.uarfcn.valid(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                    ),
                    SignalMetrics(
                        dbm = s.dbm.valid(),
                        level = s.level,
                        asu = s.asuLevel.takeIf { it in 0..96 },
                        rscp = s.dbm.valid(),
                        ecNo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) s.ecNo.valid()?.toDouble() else null,
                    ),
                    r, now,
                )
            }
            is CellInfoGsm -> {
                val id = ci.cellIdentity
                val s = ci.cellSignalStrength
                CellObservation(
                    CellIdentity(
                        radio = RadioType.GSM,
                        mcc = id.mccString?.toIntOrNull(),
                        mnc = id.mncString?.toIntOrNull(),
                        area = id.lac.valid()?.toLong(),
                        cellId = id.cid.valid()?.toLong(),
                        pci = id.bsic.valid(),
                        channel = id.arfcn.valid(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                    ),
                    SignalMetrics(
                        dbm = s.dbm.valid(),
                        level = s.level,
                        asu = s.asuLevel.takeIf { it in 0..31 },
                        rssi = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) s.rssi.valid() else null) ?: s.dbm.valid(),
                        bitErrorRate = s.bitErrorRate.valid()?.takeIf { it in 0..7 },
                        timingAdvance = s.timingAdvance.valid(),
                    ),
                    r, now,
                )
            }
            is CellInfoTdscdma -> {
                val id = ci.cellIdentity
                val s = ci.cellSignalStrength
                CellObservation(
                    CellIdentity(
                        radio = RadioType.TDSCDMA,
                        mcc = id.mccString?.toIntOrNull(),
                        mnc = id.mncString?.toIntOrNull(),
                        area = id.lac.valid()?.toLong(),
                        cellId = id.cid.valid()?.toLong(),
                        pci = id.cpid.valid(),
                        channel = id.uarfcn.valid(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                    ),
                    SignalMetrics(dbm = s.dbm.valid(), level = s.level, rscp = s.rscp.valid()),
                    r, now,
                )
            }
            is CellInfoCdma -> {
                val id: CellIdentityCdma = ci.cellIdentity
                val s = ci.cellSignalStrength
                // CDMA broadcasts position in quarter arc-seconds.
                val lat = id.latitude.valid()?.let { it / 14400.0 }
                val lon = id.longitude.valid()?.let { it / 14400.0 }
                CellObservation(
                    CellIdentity(
                        radio = RadioType.CDMA,
                        area = id.networkId.valid()?.toLong(),
                        cellId = id.basestationId.valid()?.toLong(),
                        cdmaSystemId = id.systemId.valid(),
                        operatorName = opName(id.operatorAlphaLong, id.operatorAlphaShort),
                        broadcastLat = lat?.takeIf { it != 0.0 },
                        broadcastLon = lon?.takeIf { it != 0.0 },
                    ),
                    SignalMetrics(
                        dbm = s.dbm.valid(),
                        level = s.level,
                        ecNo = s.cdmaEcio.valid()?.let { it / 10.0 },
                    ),
                    r, now,
                )
            }
            else -> null
        }
    }

    private fun nrSignal(s: CellSignalStrengthNr) = SignalMetrics(
        dbm = s.dbm.valid(),
        level = s.level,
        asu = s.asuLevel.takeIf { it in 0..97 },
        rsrp = s.ssRsrp.valid(),
        rsrq = s.ssRsrq.valid(),
        sinr = s.ssSinr.valid()?.toDouble(),
        csiRsrp = s.csiRsrp.valid(),
        csiRsrq = s.csiRsrq.valid(),
        csiSinr = s.csiSinr.valid(),
        timingAdvanceMicros = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) s.timingAdvanceMicros.valid() else null,
    )

    @SuppressLint("MissingPermission")
    private fun networkStatus(): NetworkStatus {
        val dataType = try { tm.dataNetworkType } catch (_: SecurityException) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
        val di = displayInfo
        val override = di?.overrideNetworkType ?: TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        @Suppress("DEPRECATION")
        val mmWave = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
        val nsa = override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
            override == mmWave
        val label = when {
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
            nsa -> "5G NSA"
            dataType == TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE-A Pro"
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE-A"
            else -> networkTypeName(dataType)
        }
        val serviceState: ServiceState? = try { tm.serviceState } catch (_: SecurityException) { null }
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> Transport.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        val simCount = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoCount
        } catch (_: SecurityException) { null }
        return NetworkStatus(
            operatorName = tm.networkOperatorName,
            operatorMccMnc = tm.networkOperator?.takeIf { it.length >= 5 }?.let { it.substring(0, 3) + "-" + it.substring(3) },
            simOperatorName = tm.simOperatorName,
            networkTypeLabel = label,
            isNrNsa = nsa,
            isRoaming = tm.isNetworkRoaming,
            transport = transport,
            linkDownKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            linkUpKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            cellBandwidthsKhz = serviceState?.cellBandwidths?.filter { it > 0 }.orEmpty(),
            duplexMode = serviceState?.duplexMode?.let {
                when (it) {
                    ServiceState.DUPLEX_MODE_FDD -> "FDD"
                    ServiceState.DUPLEX_MODE_TDD -> "TDD"
                    else -> null
                }
            },
            simCount = simCount,
        )
    }

    @Suppress("DEPRECATION")
    private fun networkTypeName(t: Int): String? = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> "EV-DO"
        TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        else -> null
    }
}
