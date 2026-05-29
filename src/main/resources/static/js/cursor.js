const CURSOR_TRAIL_CLASS = "cursor-trail-layer";

const TRAIL_LIFETIME_MS = 180;
const TRAIL_SPACING_PX = 2.25;
const TRAIL_MAX_POINTS = 900;
const TRAIL_BASE_WIDTH = 3.6;
const TRAIL_GLOW_WIDTH = 6.2;

function prefersFinePointer() {
  return window.matchMedia?.("(pointer: fine)").matches ?? true;
}

function prefersMotionReduction() {
  return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

function createTrailCanvas() {
  const canvas = document.createElement("canvas");
  canvas.className = CURSOR_TRAIL_CLASS;
  canvas.setAttribute("aria-hidden", "true");
  canvas.tabIndex = -1;
  canvas.style.pointerEvents = "none";
  canvas.style.position = "fixed";
  canvas.style.inset = "0";
  canvas.style.zIndex = "2147483647";
  canvas.style.width = "100vw";
  canvas.style.height = "100vh";
  canvas.style.background = "transparent";
  return canvas;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function initCursorTrail() {
  if (!prefersFinePointer() || prefersMotionReduction()) {
    return;
  }

  const canvas = createTrailCanvas();
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    return;
  }

  document.body.appendChild(canvas);

  const state = {
    width: 0,
    height: 0,
    dpr: Math.max(window.devicePixelRatio || 1, 1),
    points: [],
    rafId: 0,
    lastMoveAt: 0,
    lastFrameAt: performance.now(),
    targetX: null,
    targetY: null,
    smoothX: null,
    smoothY: null,
    lastRenderedX: null,
    lastRenderedY: null,
    active: false
  };

  const resize = () => {
    state.dpr = Math.max(window.devicePixelRatio || 1, 1);
    state.width = window.innerWidth;
    state.height = window.innerHeight;
    canvas.width = Math.round(state.width * state.dpr);
    canvas.height = Math.round(state.height * state.dpr);
    ctx.setTransform(state.dpr, 0, 0, state.dpr, 0, 0);
  };

  const scheduleRender = () => {
    if (!state.rafId) {
      state.rafId = window.requestAnimationFrame(render);
    }
  };

  const pushPoint = (x, y, time = performance.now()) => {
    const last = state.points[state.points.length - 1];
    if (!last) {
      state.points.push({ x, y, time });
      return;
    }

    const dx = x - last.x;
    const dy = y - last.y;
    const distance = Math.hypot(dx, dy);

    if (distance <= 0.001) {
      state.points[state.points.length - 1] = { x, y, time };
      return;
    }

    const steps = Math.max(1, Math.ceil(distance / TRAIL_SPACING_PX));
    for (let i = 1; i <= steps; i += 1) {
      const t = i / steps;
      state.points.push({
        x: last.x + dx * t,
        y: last.y + dy * t,
        time: time - (1 - t) * 8
      });
    }

    if (state.points.length > TRAIL_MAX_POINTS) {
      state.points.splice(0, state.points.length - TRAIL_MAX_POINTS);
    }
  };

  const trimPoints = (now) => {
    while (state.points.length && now - state.points[0].time > TRAIL_LIFETIME_MS) {
      state.points.shift();
    }
  };

  const getActivePoints = (now) => {
    const active = [];
    for (const point of state.points) {
      const age = now - point.time;
      if (age <= TRAIL_LIFETIME_MS) {
        active.push({
          ...point,
          age,
          alpha: 1 - age / TRAIL_LIFETIME_MS
        });
      }
    }
    return active;
  };

  const drawSpline = (context, points) => {
    if (points.length < 2) {
      return;
    }

    context.beginPath();
    context.moveTo(points[0].x, points[0].y);

    if (points.length === 2) {
      context.lineTo(points[1].x, points[1].y);
      context.stroke();
      return;
    }

    for (let i = 0; i < points.length - 2; i += 1) {
      const p1 = points[i];
      const p2 = points[i + 1];
      const midX = (p1.x + p2.x) / 2;
      const midY = (p1.y + p2.y) / 2;
      context.quadraticCurveTo(p1.x, p1.y, midX, midY);
    }

    const last = points[points.length - 1];
    context.lineTo(last.x, last.y);
    context.stroke();
  };

  const drawSegment = (context, a, b, alphaScale) => {
    const ageAlpha = ((a.alpha + b.alpha) / 2) * alphaScale;
    if (ageAlpha <= 0.01) {
      return;
    }

    context.globalAlpha = ageAlpha;
    context.beginPath();
    context.moveTo(a.x, a.y);
    context.lineTo(b.x, b.y);
    context.stroke();
  };

  const render = (now) => {
    state.rafId = 0;
    const dt = clamp(now - state.lastFrameAt, 8, 40);
    state.lastFrameAt = now;

    if (state.targetX != null && state.targetY != null) {
      if (state.smoothX == null || state.smoothY == null) {
        state.smoothX = state.targetX;
        state.smoothY = state.targetY;
      } else {
        const dx = state.targetX - state.smoothX;
        const dy = state.targetY - state.smoothY;
        const distance = Math.hypot(dx, dy);
        const speed = distance / dt;

        // Stabilization: strong enough to smooth out tiny shakes, light enough
        // to keep up with fast strokes without a chunky rubber-band lag.
        const followStrength = clamp(0.08 + speed * 0.015, 0.08, 0.22);
        state.smoothX += dx * followStrength;
        state.smoothY += dy * followStrength;
      }

      const prevX = state.lastRenderedX ?? state.smoothX;
      const prevY = state.lastRenderedY ?? state.smoothY;
      const delta = Math.hypot(state.smoothX - prevX, state.smoothY - prevY);
      const steps = Math.max(1, Math.ceil(delta / TRAIL_SPACING_PX));

      for (let i = 1; i <= steps; i += 1) {
        const t = i / steps;
        pushPoint(
          prevX + (state.smoothX - prevX) * t,
          prevY + (state.smoothY - prevY) * t,
          now - (1 - t) * dt
        );
      }

      state.lastRenderedX = state.smoothX;
      state.lastRenderedY = state.smoothY;
      state.active = true;
    }

    trimPoints(now);
    ctx.clearRect(0, 0, state.width, state.height);

    const activePoints = getActivePoints(now);
    if (activePoints.length >= 2) {
      ctx.save();
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.globalCompositeOperation = "source-over";

      ctx.strokeStyle = "rgba(255,255,255,0.06)";
      ctx.lineWidth = TRAIL_GLOW_WIDTH;
      ctx.shadowColor = "rgba(255,255,255,0.08)";
      ctx.shadowBlur = 2;
      drawSpline(ctx, activePoints);

      ctx.shadowBlur = 0;
      ctx.strokeStyle = "rgba(255,255,255,0.42)";
      ctx.lineWidth = TRAIL_BASE_WIDTH;
      drawSpline(ctx, activePoints);

      ctx.restore();
    }

    if (state.points.length > 0 || performance.now() - state.lastMoveAt < 120) {
      scheduleRender();
    } else {
      state.active = false;
    }
  };

  const handlePointer = (event) => {
    if (event.pointerType && event.pointerType !== "mouse" && event.pointerType !== "pen") {
      return;
    }

    state.lastMoveAt = performance.now();
    const samples =
      typeof event.getCoalescedEvents === "function" ? event.getCoalescedEvents() : null;

    const consumeSample = (sample) => {
      state.targetX = sample.clientX;
      state.targetY = sample.clientY;
    };

    if (samples && samples.length > 0) {
      for (const sample of samples) {
        consumeSample(sample);
      }
    } else {
      consumeSample(event);
    }

    scheduleRender();
  };

  const resetTrail = () => {
    state.points = [];
    state.targetX = null;
    state.targetY = null;
    state.smoothX = null;
    state.smoothY = null;
    state.lastRenderedX = null;
    state.lastRenderedY = null;
    ctx.clearRect(0, 0, state.width, state.height);
  };

  window.addEventListener("resize", resize, { passive: true });
  window.addEventListener("scroll", scheduleRender, { passive: true });
  window.addEventListener("pointermove", handlePointer, { passive: true });
  window.addEventListener("pointerrawupdate", handlePointer, { passive: true });
  window.addEventListener("pointerdown", handlePointer, { passive: true });
  window.addEventListener("pointerleave", resetTrail);
  window.addEventListener("blur", resetTrail);

  resize();
  scheduleRender();
}

document.addEventListener("DOMContentLoaded", initCursorTrail);
