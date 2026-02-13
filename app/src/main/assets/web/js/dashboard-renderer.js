/**
 * Braven Lab Dashboard — Dashboard Renderer with Sparkline Graphs
 *
 * Updates the DOM elements with live ride data received from WebSocket.
 * Features sparkline graphs for Power, VO2, Heart Rate, and Cadence.
 */
class DashboardRenderer {
  constructor() {
    // Cache DOM references
    this._els = {
      // Header / Status
      connectionBadge: document.getElementById("connectionBadge"),
      pingDot: document.getElementById("pingDot"),
      solidDot: document.getElementById("solidDot"),
      connectionText: document.getElementById("connectionText"),
      elapsedTime: document.getElementById("elapsedTime"),
      batteryPercent: document.getElementById("batteryPercent"),
      batteryIcon: document.getElementById("batteryIcon"),
      distance: document.getElementById("distance"),

      // Time Strip (Prominent timers)
      sessionTime: document.getElementById("sessionTime"),
      lapTimeBig: document.getElementById("lapTimeBig"),
      lapNumberBig: document.getElementById("lapNumberBig"),

      // Power (Primary)
      power: document.getElementById("power"),
      power3sAvg: document.getElementById("power3sAvg"),
      powerZoneBadge: document.getElementById("powerZoneBadge"),
      lapPower: document.getElementById("lapPower"),
      lapNormalizedPower: document.getElementById("lapNormalizedPower"),
      lapMaxPower: document.getElementById("lapMaxPower"),
      powerGraph: document.getElementById("powerGraph"),

      // VO2 (Placeholder)
      vo2: document.getElementById("vo2"),
      vo2Percent: document.getElementById("vo2Percent"),
      vo2LiveBadge: document.getElementById("vo2LiveBadge"),
      vo2Graph: document.getElementById("vo2Graph"),

      // Heart Rate
      heartRate: document.getElementById("heartRate"),
      maxHeartRate: document.getElementById("maxHeartRate"),
      lapHeartRate: document.getElementById("lapHeartRate"),
      hrLiveBadge: document.getElementById("hrLiveBadge"),
      hrGraph: document.getElementById("hrGraph"),

      // Cadence
      cadence: document.getElementById("cadence"),
      avgCadence: document.getElementById("avgCadence"),
      lapCadence: document.getElementById("lapCadence"),
      cadenceGraph: document.getElementById("cadenceGraph"),

      // Speed (Secondary)
      speed: document.getElementById("speed"),
      averageSpeed: document.getElementById("averageSpeed"),
      lapSpeed: document.getElementById("lapSpeed"),

      // Lap Info
      lapNumber: document.getElementById("lapNumber"),
      lapTime: document.getElementById("lapTime"),

      // Secondary Metrics
      coreTemp: document.getElementById("coreTemp"),
      coreTempTrend: document.getElementById("coreTempTrend"),
      lastLapPower: document.getElementById("lastLapPower"),
      lastLapTime: document.getElementById("lastLapTime"),
      lastLapSpeed: document.getElementById("lastLapSpeed"),
      temperature: document.getElementById("temperature"),
      elevation: document.getElementById("elevation"),
      grade: document.getElementById("grade"),
      latitude: document.getElementById("latitude"),
      longitude: document.getElementById("longitude"),
      lastUpdate: document.getElementById("lastUpdate"),

      // Lap List Table
      lapListBody: document.getElementById("lapListBody"),
      currentLapRow: document.getElementById("currentLapRow"),

      // Lactate
      lactateValue: document.getElementById("lactateValue"),
      lactateTimestamp: document.getElementById("lactateTimestamp"),

      // Trainer Control
      trainerStateBadge: document.getElementById("trainerStateBadge"),
      trainerScanSection: document.getElementById("trainerScanSection"),
      trainerControlSection: document.getElementById("trainerControlSection"),
      trainerDeviceName: document.getElementById("trainerDeviceName"),
      trainerTargetDisplay: document.getElementById("trainerTargetDisplay"),
      trainerError: document.getElementById("trainerError"),
      trainerDeviceList: document.getElementById("trainerDeviceList"),
    };

    // ─── Graph Data Buffers (Full Session - No Limit) ─────
    this._powerHistory = [];
    this._power3sHistory = [];
    this._hrHistory = [];
    this._cadenceHistory = [];
    this._vo2History = [];

    // ─── Lap History Tracking ─────────────────────────────
    this._lapHistory = []; // Array of completed laps
    this._currentLapNumber = 0;
    // ─── Lactate Tracking ───────────────────────────
    this._currentLactate = null; // Latest lactate reading (mmol/L)
    this._lactateTimestamp = null; // When last reading was taken
    // ─── Graph Colors ─────────────────────────────────────
    this._graphColors = {
      power: {
        line: "#a855f7",
        fill: "rgba(168, 85, 247, 0.15)",
        secondary: "#c4b5fd",
      },
      vo2: { line: "#06b6d4", fill: "rgba(6, 182, 212, 0.15)" },
      hr: { line: "#f43f5e", fill: "rgba(244, 63, 94, 0.15)" },
      cadence: { line: "#3b82f6", fill: "rgba(59, 130, 246, 0.15)" },
    };

    // ─── Tracking ─────────────────────────────────────────
    this._prevCoreTemp = null;
    this._cadenceSum = 0;
    this._cadenceCount = 0;

    // Zone names for power
    this._zoneNames = ["", "Z1", "Z2", "Z3", "Z4", "Z5", "Z6", "Z7"];

    // Initialize canvas contexts
    this._initGraphs();
  }

  // ═══════════════════════════════════════════════════════
  // GRAPH INITIALIZATION
  // ═══════════════════════════════════════════════════════

  _initGraphs() {
    this._powerCtx = this._getContext("powerGraph");
    this._vo2Ctx = this._getContext("vo2Graph");
    this._hrCtx = this._getContext("hrGraph");
    this._cadenceCtx = this._getContext("cadenceGraph");

    // Set up resize observer for responsive canvases
    if (typeof ResizeObserver !== "undefined") {
      const observer = new ResizeObserver(() => this._resizeCanvases());
      ["powerGraph", "vo2Graph", "hrGraph", "cadenceGraph"].forEach((id) => {
        const el = document.getElementById(id);
        if (el) observer.observe(el.parentElement);
      });
    }

    // Initial resize
    setTimeout(() => this._resizeCanvases(), 100);
  }

  _getContext(canvasId) {
    const canvas = document.getElementById(canvasId);
    return canvas ? canvas.getContext("2d") : null;
  }

  _resizeCanvases() {
    ["powerGraph", "vo2Graph", "hrGraph", "cadenceGraph"].forEach((id) => {
      const canvas = document.getElementById(id);
      if (canvas && canvas.parentElement) {
        const rect = canvas.parentElement.getBoundingClientRect();
        canvas.width = rect.width;
        canvas.height = rect.height;
      }
    });
    // Redraw after resize
    this._drawAllGraphs();
  }

  // ═══════════════════════════════════════════════════════
  // MAIN UPDATE
  // ═══════════════════════════════════════════════════════

  update(data) {
    this._updatePower(data);
    this._updateVO2(data);
    this._updateHeartRate(data);
    this._updateCadence(data);
    this._updateSpeed(data);
    this._updateLapData(data);
    this._updateSecondaryMetrics(data);
    this._updateLactate(data);
    this._updateTrainer(data);
    this._updateHeader(data);
    this._drawAllGraphs();
  }

  // ═══════════════════════════════════════════════════════
  // POWER (Primary Metric)
  // ═══════════════════════════════════════════════════════

  _updatePower(data) {
    // Instant power
    if (this._els.power && data.power !== undefined) {
      this._els.power.textContent = data.power > 0 ? data.power : "--";

      // Add to history (full session)
      if (data.power > 0) {
        this._powerHistory.push(data.power);
      }
    }

    // 3-second average
    if (this._els.power3sAvg && data.power3sAvg !== undefined) {
      this._els.power3sAvg.textContent =
        data.power3sAvg > 0 ? data.power3sAvg : "--";

      // Add to 3s history (full session)
      if (data.power3sAvg > 0) {
        this._power3sHistory.push(data.power3sAvg);
      }
    }

    // Power zone badge
    if (this._els.powerZoneBadge && data.powerZone !== undefined) {
      this._els.powerZoneBadge.textContent =
        data.powerZone > 0 ? `Zone ${data.powerZone}` : "--";
    }

    // Lap power stats
    if (this._els.lapPower && data.lapPower !== undefined) {
      this._els.lapPower.textContent =
        data.lapPower > 0 ? `${Math.round(data.lapPower)} W` : "-- W";
    }
    if (this._els.lapNormalizedPower && data.lapNormalizedPower !== undefined) {
      this._els.lapNormalizedPower.textContent =
        data.lapNormalizedPower > 0
          ? `${Math.round(data.lapNormalizedPower)} W`
          : "-- W";
    }
    if (this._els.lapMaxPower && data.lapMaxPower !== undefined) {
      this._els.lapMaxPower.textContent =
        data.lapMaxPower > 0 ? `${Math.round(data.lapMaxPower)} W` : "-- W";
    }
  }

  // ═══════════════════════════════════════════════════════
  // VO2 (Placeholder for future sensor)
  // ═══════════════════════════════════════════════════════

  _updateVO2(data) {
    // VO2 sensor not yet connected - show placeholder
    if (this._els.vo2) {
      if (data.vo2 !== undefined && data.vo2 > 0) {
        this._els.vo2.textContent = data.vo2.toFixed(1);
        this._vo2History.push(data.vo2);
        if (this._els.vo2LiveBadge) {
          this._els.vo2LiveBadge.textContent = "Live";
          this._els.vo2LiveBadge.className =
            "text-xs font-medium text-cyan-400 animate-pulsefast";
        }
      } else {
        this._els.vo2.textContent = "--";
        if (this._els.vo2LiveBadge) {
          this._els.vo2LiveBadge.textContent = "Pending";
          this._els.vo2LiveBadge.className =
            "text-xs font-medium text-neutral-500";
        }
      }
    }

    if (this._els.vo2Percent) {
      if (data.vo2Percent !== undefined && data.vo2Percent > 0) {
        this._els.vo2Percent.textContent = `${data.vo2Percent.toFixed(0)}%`;
      } else {
        this._els.vo2Percent.textContent = "--%";
      }
    }
  }

  // ═══════════════════════════════════════════════════════
  // HEART RATE
  // ═══════════════════════════════════════════════════════

  _updateHeartRate(data) {
    if (this._els.heartRate && data.heartRate !== undefined) {
      this._els.heartRate.textContent =
        data.heartRate > 0 ? data.heartRate : "--";

      if (data.heartRate > 0) {
        this._hrHistory.push(data.heartRate);
      }
    }

    if (this._els.maxHeartRate && data.maxHeartRate !== undefined) {
      this._els.maxHeartRate.textContent =
        data.maxHeartRate > 0 ? data.maxHeartRate : "--";
    }

    if (this._els.lapHeartRate && data.lapHeartRate !== undefined) {
      this._els.lapHeartRate.textContent =
        data.lapHeartRate > 0 ? Math.round(data.lapHeartRate) : "--";
    }
  }

  // ═══════════════════════════════════════════════════════
  // CADENCE
  // ═══════════════════════════════════════════════════════

  _updateCadence(data) {
    if (this._els.cadence && data.cadence !== undefined) {
      this._els.cadence.textContent = data.cadence > 0 ? data.cadence : "--";

      if (data.cadence > 0) {
        this._cadenceHistory.push(data.cadence);

        // Track for average
        this._cadenceSum += data.cadence;
        this._cadenceCount++;
      }
    }

    if (this._els.avgCadence) {
      if (this._cadenceCount > 0) {
        this._els.avgCadence.textContent = Math.round(
          this._cadenceSum / this._cadenceCount,
        );
      } else {
        this._els.avgCadence.textContent = "--";
      }
    }

    if (this._els.lapCadence && data.lapCadence !== undefined) {
      this._els.lapCadence.textContent =
        data.lapCadence > 0 ? Math.round(data.lapCadence) : "--";
    }
  }

  // ═══════════════════════════════════════════════════════
  // SPEED (Secondary)
  // ═══════════════════════════════════════════════════════

  _updateSpeed(data) {
    if (this._els.speed && data.speed !== undefined) {
      this._els.speed.textContent =
        data.speed > 0 ? data.speed.toFixed(1) : "--";
    }
    if (this._els.averageSpeed && data.averageSpeed !== undefined) {
      this._els.averageSpeed.textContent =
        data.averageSpeed > 0 ? data.averageSpeed.toFixed(1) : "--";
    }
    if (this._els.lapSpeed && data.lapSpeed !== undefined) {
      this._els.lapSpeed.textContent =
        data.lapSpeed > 0 ? data.lapSpeed.toFixed(1) : "--";
    }
  }

  // ═══════════════════════════════════════════════════════
  // LAP DATA
  // ═══════════════════════════════════════════════════════

  _updateLapData(data) {
    // Detect lap change - when lap number increases, save the completed lap
    if (
      data.lapNumber !== undefined &&
      data.lapNumber > this._currentLapNumber
    ) {
      // If we had a previous lap (not the first), save it to history
      if (this._currentLapNumber > 0 && data.lastLapTime > 0) {
        this._lapHistory.push({
          number: this._currentLapNumber,
          time: data.lastLapTime,
          power: data.lastLapPower || 0,
          speed: data.lastLapSpeed || 0,
          heartRate: this._lastLapHR || 0,
          cadence: this._lastLapCadence || 0,
          lactate: this._currentLactate,
        });
        this._renderLapList();
      }
      this._currentLapNumber = data.lapNumber;
    }

    // Track current lap metrics for when lap completes
    if (data.lapHeartRate !== undefined) this._lastLapHR = data.lapHeartRate;
    if (data.lapCadence !== undefined) this._lastLapCadence = data.lapCadence;

    // Update old lap display elements (for backwards compat)
    if (this._els.lapNumber && data.lapNumber !== undefined) {
      this._els.lapNumber.textContent =
        data.lapNumber > 0 ? data.lapNumber : "1";
    }
    if (this._els.lapTime && data.lapTime !== undefined) {
      this._els.lapTime.textContent =
        data.lapTime > 0 ? this._formatTime(data.lapTime) : "00:00";
    }

    // Render current lap in table
    this._renderCurrentLapRow(data);
  }

  /**
   * Render the current (in-progress) lap row at the top of the table
   */
  _renderCurrentLapRow(data) {
    if (!this._els.currentLapRow) return;

    const lapNum = data.lapNumber || 1;
    const lapTime = data.lapTime || 0;
    const lapPower = data.lapPower || 0;
    const lapHR = data.lapHeartRate || 0;
    const lapCadence = data.lapCadence || 0;

    this._els.currentLapRow.innerHTML = `
      <td class="px-3 py-2 text-cyan-400 font-semibold">${lapNum}</td>
      <td class="px-3 py-2 font-mono text-cyan-300">${this._formatTime(lapTime)}</td>
      <td class="px-3 py-2 font-semibold text-cyan-400">${lapPower > 0 ? lapPower : "--"}</td>
      <td class="px-3 py-2 text-cyan-300">${lapHR > 0 ? lapHR : "--"}</td>
      <td class="px-3 py-2 text-cyan-300">${lapCadence > 0 ? lapCadence : "--"}</td>
      <td class="px-3 py-2 text-cyan-300 font-mono">${this._currentLactate !== null ? this._currentLactate.toFixed(1) : "--"}</td>
    `;
  }

  /**
   * Render the completed laps list (newest first)
   */
  _renderLapList() {
    if (!this._els.lapListBody) return;

    // Clear existing rows
    this._els.lapListBody.innerHTML = "";

    // Render laps in reverse order (newest first)
    for (let i = this._lapHistory.length - 1; i >= 0; i--) {
      const lap = this._lapHistory[i];
      const row = document.createElement("tr");
      row.className = "border-b border-neutral-800 hover:bg-neutral-800/50";
      row.innerHTML = `
        <td class="px-3 py-2 text-neutral-400">${lap.number}</td>
        <td class="px-3 py-2 font-mono">${this._formatTime(lap.time)}</td>
        <td class="px-3 py-2 font-semibold text-orange-400">${lap.power > 0 ? lap.power : "--"}</td>
        <td class="px-3 py-2">${lap.heartRate > 0 ? lap.heartRate : "--"}</td>
        <td class="px-3 py-2">${lap.cadence > 0 ? lap.cadence : "--"}</td>
        <td class="px-3 py-2 font-mono text-rose-400">${lap.lactate !== null && lap.lactate !== undefined ? lap.lactate.toFixed(1) : "--"}</td>
      `;
      this._els.lapListBody.appendChild(row);
    }
  }

  // ═══════════════════════════════════════════════════════
  // LACTATE
  // ═══════════════════════════════════════════════════════

  _updateLactate(data) {
    if (data.lactate !== undefined && data.lactate !== null) {
      this._currentLactate = data.lactate;
      this._lactateTimestamp = data.lactateTimestamp || Date.now();

      if (this._els.lactateValue) {
        this._els.lactateValue.textContent = data.lactate.toFixed(1);
      }
      if (this._els.lactateTimestamp && this._lactateTimestamp) {
        const ago = this._formatTimeAgo(this._lactateTimestamp);
        this._els.lactateTimestamp.textContent = `Last reading ${ago}`;
      }
    }
  }

  _formatTimeAgo(timestamp) {
    const diffMs = Date.now() - timestamp;
    if (diffMs < 5000) return "just now";
    if (diffMs < 60000) return `${Math.floor(diffMs / 1000)}s ago`;
    if (diffMs < 3600000) return `${Math.floor(diffMs / 60000)}m ago`;
    return `${Math.floor(diffMs / 3600000)}h ago`;
  }

  // ═══════════════════════════════════════════════════════
  // TRAINER CONTROL (KICKR via BLE FTMS)
  // ═══════════════════════════════════════════════════════

  _updateTrainer(data) {
    const state = data.trainerState;
    if (!state) return;

    const badge = this._els.trainerStateBadge;
    const scanSection = this._els.trainerScanSection;
    const controlSection = this._els.trainerControlSection;
    const errorEl = this._els.trainerError;

    // State badge styling
    if (badge) {
      const stateConfig = {
        DISCONNECTED: {
          text: "Disconnected",
          cls: "bg-neutral-700/50 text-neutral-500",
        },
        SCANNING: {
          text: "Scanning",
          cls: "bg-amber-500/20 text-amber-400 animate-pulse",
        },
        CONNECTING: {
          text: "Connecting",
          cls: "bg-amber-500/20 text-amber-400 animate-pulse",
        },
        CONNECTED: { text: "Connected", cls: "bg-blue-500/20 text-blue-400" },
        CONTROLLING: {
          text: "Controlling",
          cls: "bg-green-500/20 text-green-400",
        },
        ERROR: { text: "Error", cls: "bg-red-500/20 text-red-400" },
      };
      const cfg = stateConfig[state] || stateConfig.DISCONNECTED;
      badge.textContent = cfg.text;
      badge.className = `ml-auto px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest rounded-full ${cfg.cls}`;
    }

    // Show/hide sections based on state
    const isConnected = state === "CONNECTED" || state === "CONTROLLING";
    if (scanSection) scanSection.classList.toggle("hidden", isConnected);
    if (controlSection) controlSection.classList.toggle("hidden", !isConnected);

    // Device name
    if (this._els.trainerDeviceName && data.trainerDeviceName) {
      this._els.trainerDeviceName.textContent = data.trainerDeviceName;
    }

    // Target power display
    if (this._els.trainerTargetDisplay && data.trainerTargetPower != null) {
      this._els.trainerTargetDisplay.textContent = data.trainerTargetPower;
      // Update the global tracking variable for +/- adjustments
      if (typeof _currentTrainerTarget !== "undefined") {
        window._currentTrainerTarget = data.trainerTargetPower;
      }
    }

    // Error message
    if (errorEl) {
      if (data.trainerError) {
        errorEl.textContent = data.trainerError;
        errorEl.classList.remove("hidden");
      } else {
        errorEl.classList.add("hidden");
      }
    }
  }

  // ═══════════════════════════════════════════════════════
  // SECONDARY METRICS
  // ═══════════════════════════════════════════════════════

  _updateSecondaryMetrics(data) {
    // Core temp
    if (this._els.coreTemp && data.coreTemp !== undefined) {
      this._els.coreTemp.textContent =
        data.coreTemp > 0 ? data.coreTemp.toFixed(1) : "--";

      // Trend
      if (this._els.coreTempTrend && data.coreTemp > 0) {
        if (this._prevCoreTemp !== null) {
          const delta = data.coreTemp - this._prevCoreTemp;
          const sign = delta >= 0 ? "+" : "";
          const iconName =
            delta > 0.05
              ? "arrow-up-right"
              : delta < -0.05
                ? "arrow-down-right"
                : "minus";
          const color =
            delta > 0.05
              ? "text-orange-400"
              : delta < -0.05
                ? "text-blue-400"
                : "text-neutral-500";

          this._els.coreTempTrend.className = `text-xs ${color} flex items-center gap-1`;
          this._els.coreTempTrend.innerHTML = `${sign}${delta.toFixed(2)}° <i data-lucide="${iconName}" class="w-3 h-3"></i>`;

          if (typeof lucide !== "undefined") {
            lucide.createIcons({
              nodes: this._els.coreTempTrend.querySelectorAll("[data-lucide]"),
            });
          }
        }
        this._prevCoreTemp = data.coreTemp;
      }
    }

    // Environment
    if (this._els.temperature && data.temperature !== undefined) {
      this._els.temperature.textContent =
        data.temperature > 0 ? data.temperature.toFixed(0) : "--";
    }
    if (this._els.elevation && data.elevation !== undefined) {
      this._els.elevation.textContent =
        data.elevation > 0 ? Math.round(data.elevation) : "--";
    }
    if (this._els.grade && data.grade !== undefined) {
      this._els.grade.textContent =
        data.grade !== 0 ? data.grade.toFixed(1) : "0.0";
    }

    // GPS
    if (this._els.latitude && data.latitude !== undefined) {
      this._els.latitude.textContent = data.latitude.toFixed(5);
    }
    if (this._els.longitude && data.longitude !== undefined) {
      this._els.longitude.textContent = data.longitude.toFixed(5);
    }
  }

  // ═══════════════════════════════════════════════════════
  // HEADER
  // ═══════════════════════════════════════════════════════

  _updateHeader(data) {
    // Time Strip - Session Time (prominent)
    if (this._els.sessionTime && data.elapsedTime !== undefined) {
      this._els.sessionTime.textContent = this._formatTime(data.elapsedTime);
    }
    // Time Strip - Lap Time (prominent)
    if (this._els.lapTimeBig && data.lapTime !== undefined) {
      this._els.lapTimeBig.textContent =
        data.lapTime > 0 ? this._formatTime(data.lapTime) : "00:00";
    }
    // Time Strip - Lap Number
    if (this._els.lapNumberBig && data.lapNumber !== undefined) {
      this._els.lapNumberBig.textContent =
        data.lapNumber > 0 ? data.lapNumber : "1";
    }

    // Header bar elapsed (smaller, for reference)
    if (this._els.elapsedTime && data.elapsedTime !== undefined) {
      this._els.elapsedTime.textContent = this._formatTime(data.elapsedTime);
    }
    if (this._els.distance && data.distance !== undefined) {
      this._els.distance.textContent =
        data.distance > 0 ? `${data.distance.toFixed(2)} km` : "0.00 km";
    }
    if (this._els.batteryPercent && data.batteryPercent !== undefined) {
      this._els.batteryPercent.textContent =
        data.batteryPercent >= 0 ? `${data.batteryPercent}%` : "--%";
    }
    if (this._els.lastUpdate) {
      this._els.lastUpdate.textContent = `Updated ${new Date().toLocaleTimeString()}`;
    }
  }

  // ═══════════════════════════════════════════════════════
  // SPARKLINE GRAPH RENDERING
  // ═══════════════════════════════════════════════════════

  _drawAllGraphs() {
    this._drawSparkline(
      this._powerCtx,
      this._power3sHistory,
      this._graphColors.power,
      {
        showFill: true,
        secondary: this._powerHistory,
        secondaryColor: this._graphColors.power.secondary,
      },
    );
    this._drawSparkline(this._vo2Ctx, this._vo2History, this._graphColors.vo2, {
      showFill: true,
    });
    this._drawSparkline(this._hrCtx, this._hrHistory, this._graphColors.hr, {
      showFill: true,
    });
    this._drawSparkline(
      this._cadenceCtx,
      this._cadenceHistory,
      this._graphColors.cadence,
      { showFill: true },
    );
  }

  _drawSparkline(ctx, data, colors, options = {}) {
    if (!ctx || !data || data.length < 2) return;

    const canvas = ctx.canvas;
    const width = canvas.width;
    const height = canvas.height;
    const padding = 4;

    // Clear
    ctx.clearRect(0, 0, width, height);

    // Calculate bounds
    const min = Math.min(...data) * 0.9;
    const max = Math.max(...data) * 1.1;
    const range = max - min || 1;

    const xStep = (width - padding * 2) / (data.length - 1);

    // Helper to get Y position
    const getY = (val) =>
      height - padding - ((val - min) / range) * (height - padding * 2);

    // Draw secondary line (instant power behind 3s avg)
    if (
      options.secondary &&
      options.secondary.length > 1 &&
      options.secondaryColor
    ) {
      ctx.beginPath();
      ctx.strokeStyle = options.secondaryColor;
      ctx.lineWidth = 1;
      ctx.globalAlpha = 0.3;

      const secMin = Math.min(...options.secondary) * 0.9;
      const secMax = Math.max(...options.secondary) * 1.1;
      const secRange = secMax - secMin || 1;
      const secXStep = (width - padding * 2) / (options.secondary.length - 1);
      const getSecY = (val) =>
        height - padding - ((val - secMin) / secRange) * (height - padding * 2);

      options.secondary.forEach((val, i) => {
        const x = padding + i * secXStep;
        const y = getSecY(val);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
      ctx.globalAlpha = 1;
    }

    // Draw fill
    if (options.showFill) {
      ctx.beginPath();
      ctx.moveTo(padding, height - padding);
      data.forEach((val, i) => {
        const x = padding + i * xStep;
        const y = getY(val);
        ctx.lineTo(x, y);
      });
      ctx.lineTo(padding + (data.length - 1) * xStep, height - padding);
      ctx.closePath();
      ctx.fillStyle = colors.fill;
      ctx.fill();
    }

    // Draw main line
    ctx.beginPath();
    ctx.strokeStyle = colors.line;
    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    data.forEach((val, i) => {
      const x = padding + i * xStep;
      const y = getY(val);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Draw current value dot
    const lastX = padding + (data.length - 1) * xStep;
    const lastY = getY(data[data.length - 1]);
    ctx.beginPath();
    ctx.arc(lastX, lastY, 4, 0, Math.PI * 2);
    ctx.fillStyle = colors.line;
    ctx.fill();
    ctx.beginPath();
    ctx.arc(lastX, lastY, 2, 0, Math.PI * 2);
    ctx.fillStyle = "#fff";
    ctx.fill();
  }

  // ═══════════════════════════════════════════════════════
  // CONNECTION STATUS
  // ═══════════════════════════════════════════════════════

  setConnectionStatus(connected) {
    const badge = this._els.connectionBadge;
    const ping = this._els.pingDot;
    const solid = this._els.solidDot;
    const text = this._els.connectionText;

    if (badge) {
      badge.className = connected
        ? "flex items-center gap-2 px-3 py-1 rounded-full bg-green-500/10 border border-green-500/20"
        : "flex items-center gap-2 px-3 py-1 rounded-full bg-red-500/10 border border-red-500/20";
    }
    if (ping) {
      ping.className = connected
        ? "animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"
        : "absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75";
    }
    if (solid) {
      solid.className = connected
        ? "relative inline-flex rounded-full h-2 w-2 bg-green-500"
        : "relative inline-flex rounded-full h-2 w-2 bg-red-500";
    }
    if (text) {
      text.className = connected
        ? "text-xs font-medium text-green-500 tracking-wide uppercase"
        : "text-xs font-medium text-red-500 tracking-wide uppercase";
      text.textContent = connected ? "Live" : "Disconnected";
    }
  }

  // ═══════════════════════════════════════════════════════
  // UTILITIES
  // ═══════════════════════════════════════════════════════

  _formatTime(totalSeconds) {
    const seconds = Math.floor(totalSeconds);
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    const pad = (n) => n.toString().padStart(2, "0");
    return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
  }
}
