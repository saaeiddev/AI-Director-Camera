(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const video = $('camera');
  const overlay = $('overlay');
  const ctx = overlay.getContext('2d');
  const analysisCanvas = $('analysisCanvas');
  const analysisCtx = analysisCanvas.getContext('2d', { willReadFrequently: true });

  const techniques = {
    WIDE: { label: 'Wide', description: 'Keep environment visible; subject height roughly 18–55% of frame.' },
    MEDIUM: { label: 'Medium', description: 'Balanced subject framing; eyes near upper third.' },
    CLOSE_UP: { label: 'Close-Up', description: 'Fill the frame with the subject while preserving clean headroom.' },
    HERO: { label: 'Hero', description: 'Low-angle medium hero framing with controlled movement.' },
    TRACKING: { label: 'Tracking', description: 'Move parallel with the subject; keep center drift controlled.' },
    DOLLY: { label: 'Dolly', description: 'Push toward or away from the subject on a stable line.' },
    ORBIT: { label: 'Orbit', description: 'Arc around the subject while preserving distance and framing.' }
  };

  const state = {
    stream: null,
    facingMode: 'environment',
    mediaRecorder: null,
    chunks: [],
    lastBlob: null,
    lastFile: null,
    selectedTechnique: 'HERO',
    coachEnabled: false,
    detector: null,
    detectorLoading: false,
    detections: [],
    averageLuma: 100,
    highlightFraction: 0,
    horizon: 0,
    smoothness: 100,
    motionEnabled: false,
    lastMotionMag: 0,
    activeProjectId: safeStorageGet('aidc.activeProjectId', ''),
    projects: safeJsonParse(safeStorageGet('aidc.projects', '[]'), []),
    lastAnalysis: 0,
    recordingStartedAt: 0,
    zoomCap: null
  };

  function safeStorageGet(key, fallback) {
    try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
  }

  function safeJsonParse(value, fallback) {
    try { return JSON.parse(value); } catch { return fallback; }
  }

  function loadScript(src, timeoutMs = 10000) {
    return new Promise((resolve, reject) => {
      const existing = document.querySelector(`script[data-dynamic-src="${src}"]`);
      if (existing?.dataset.loaded === '1') return resolve();
      if (existing) existing.remove();

      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.crossOrigin = 'anonymous';
      script.dataset.dynamicSrc = src;

      const timer = setTimeout(() => {
        script.remove();
        reject(new Error(`Timed out loading ${src}`));
      }, timeoutMs);

      script.onload = () => {
        clearTimeout(timer);
        script.dataset.loaded = '1';
        resolve();
      };
      script.onerror = () => {
        clearTimeout(timer);
        script.remove();
        reject(new Error(`Failed to load ${src}`));
      };
      document.head.appendChild(script);
    });
  }

  function toast(message) {
    const el = $('toast');
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.remove('show'), 2200);
  }

  function switchView(id) {
    document.querySelectorAll('.screen').forEach(v => v.classList.toggle('active', v.id === id));
    document.querySelectorAll('.tabbar button').forEach(b => b.classList.toggle('active', b.dataset.view === id));
    if (id === 'projectsView') renderProjects();
  }

  document.querySelectorAll('.tabbar button').forEach(btn => btn.addEventListener('click', () => switchView(btn.dataset.view)));

  function resizeOverlay() {
    const dpr = Math.min(devicePixelRatio || 1, 2);
    const rect = overlay.getBoundingClientRect();
    overlay.width = Math.round(rect.width * dpr);
    overlay.height = Math.round(rect.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  addEventListener('resize', resizeOverlay, { passive: true });
  resizeOverlay();

  async function startCamera() {
    if (!navigator.mediaDevices?.getUserMedia) {
      toast('Camera API unavailable. Open this page over HTTPS in Safari.');
      return;
    }
    stopCamera();
    const constraints = {
      audio: true,
      video: {
        facingMode: { ideal: state.facingMode },
        width: { ideal: 1920 },
        height: { ideal: 1080 },
        frameRate: { ideal: 30, max: 60 }
      }
    };
    try {
      state.stream = await navigator.mediaDevices.getUserMedia(constraints);
      video.srcObject = state.stream;
      await video.play();
      $('recordBtn').disabled = false;
      $('flipBtn').disabled = false;
      $('startBtn').textContent = 'RESTART CAMERA';
      configureTrackControls();
      updateQualityPill();
      ensureDetector();
      toast('Camera ready');
    } catch (err) {
      console.error(err);
      $('guidanceText').textContent = 'Camera permission was denied or unavailable.';
      toast(err?.message || 'Could not open camera');
    }
  }

  function stopCamera() {
    if (state.stream) state.stream.getTracks().forEach(t => t.stop());
    state.stream = null;
    video.srcObject = null;
    $('recordBtn').disabled = true;
    $('flipBtn').disabled = true;
  }

  function configureTrackControls() {
    const track = state.stream?.getVideoTracks?.()[0];
    if (!track) return;
    const settings = track.getSettings?.() || {};
    const caps = track.getCapabilities?.() || {};
    state.zoomCap = caps.zoom || null;
    if (state.zoomCap) {
      $('zoomPanel').hidden = false;
      $('zoomSlider').min = state.zoomCap.min;
      $('zoomSlider').max = state.zoomCap.max;
      $('zoomSlider').step = state.zoomCap.step || 0.1;
      $('zoomSlider').value = settings.zoom || 1;
      $('zoomLabel').textContent = `${Number($('zoomSlider').value).toFixed(1)}×`;
    } else {
      $('zoomPanel').hidden = true;
    }
    const torchSupported = !!caps.torch;
    $('torchBtn').hidden = !torchSupported;
    $('torchBtn').dataset.on = '0';
  }

  function updateQualityPill() {
    const track = state.stream?.getVideoTracks?.()[0];
    const s = track?.getSettings?.() || {};
    $('qualityPill').textContent = s.width && s.height ? `${s.width}×${s.height} • C —` : 'LIVE • C —';
  }

  $('startBtn').addEventListener('click', startCamera);
  $('flipBtn').addEventListener('click', async () => {
    state.facingMode = state.facingMode === 'environment' ? 'user' : 'environment';
    await startCamera();
  });

  $('zoomSlider').addEventListener('input', async (e) => {
    const track = state.stream?.getVideoTracks?.()[0];
    if (!track || !state.zoomCap) return;
    const value = Number(e.target.value);
    $('zoomLabel').textContent = `${value.toFixed(1)}×`;
    try { await track.applyConstraints({ advanced: [{ zoom: value }] }); } catch (err) { console.warn(err); }
  });

  $('torchBtn').addEventListener('click', async () => {
    const track = state.stream?.getVideoTracks?.()[0];
    if (!track) return;
    const on = $('torchBtn').dataset.on !== '1';
    try {
      await track.applyConstraints({ advanced: [{ torch: on }] });
      $('torchBtn').dataset.on = on ? '1' : '0';
      $('torchBtn').textContent = on ? 'TORCH ON' : 'TORCH';
    } catch { toast('Torch control is not available in this Safari session.'); }
  });

  function bestRecordingMime() {
    const choices = [
      'video/mp4;codecs=h264,aac',
      'video/mp4;codecs=avc1,mp4a.40.2',
      'video/mp4',
      'video/webm;codecs=vp9,opus',
      'video/webm;codecs=vp8,opus',
      'video/webm'
    ];
    return choices.find(t => window.MediaRecorder?.isTypeSupported?.(t)) || '';
  }

  async function toggleRecording() {
    if (!state.stream) return;
    if (state.mediaRecorder?.state === 'recording') {
      state.mediaRecorder.stop();
      return;
    }
    if (!window.MediaRecorder) { toast('MediaRecorder is unavailable in this browser.'); return; }
    state.chunks = [];
    const mimeType = bestRecordingMime();
    try {
      state.mediaRecorder = new MediaRecorder(state.stream, mimeType ? { mimeType } : undefined);
      state.recordingStartedAt = performance.now();
      state.mediaRecorder.ondataavailable = e => { if (e.data?.size) state.chunks.push(e.data); };
      state.mediaRecorder.onstop = handleRecordingStop;
      state.mediaRecorder.onerror = e => toast(e.error?.message || 'Recording failed');
      state.mediaRecorder.start(1000);
      $('recordBtn').classList.add('recording');
      $('guidanceText').textContent = 'Recording… keep movement smooth.';
      if (navigator.vibrate) navigator.vibrate(30);
    } catch (err) {
      console.error(err);
      toast('Could not start recording.');
    }
  }
  $('recordBtn').addEventListener('click', toggleRecording);

  async function handleRecordingStop() {
    $('recordBtn').classList.remove('recording');
    const type = state.mediaRecorder?.mimeType || 'video/mp4';
    const blob = new Blob(state.chunks, { type });
    state.lastBlob = blob;
    const ext = type.includes('webm') ? 'webm' : 'mp4';
    const file = new File([blob], `AI-Director-Camera-${new Date().toISOString().replace(/[:.]/g,'-')}.${ext}`, { type });
    state.lastFile = file;
    $('shareLastBtn').disabled = false;
    showClip(blob, file);
    saveShotMetadata(Math.max(0, performance.now() - state.recordingStartedAt));
  }

  function showClip(blob, file) {
    const url = URL.createObjectURL(blob);
    $('clipPreview').src = url;
    $('downloadClipBtn').href = url;
    $('downloadClipBtn').download = file.name;
    $('clipDialog').showModal();
  }
  $('closeDialogBtn').addEventListener('click', () => $('clipDialog').close());
  $('shareLastBtn').addEventListener('click', () => { if (state.lastBlob && state.lastFile) showClip(state.lastBlob, state.lastFile); });
  $('shareClipBtn').addEventListener('click', async () => {
    if (!state.lastFile) return;
    try {
      if (navigator.canShare?.({ files: [state.lastFile] })) {
        await navigator.share({ title: 'AI Director Camera', files: [state.lastFile] });
      } else {
        toast('Use Download to save the clip from this browser.');
      }
    } catch (err) {
      if (err?.name !== 'AbortError') toast('Share failed; use Download instead.');
    }
  });

  async function enableMotion() {
    try {
      if (typeof window.DeviceOrientationEvent?.requestPermission === 'function') {
        const result = await window.DeviceOrientationEvent.requestPermission();
        if (result !== 'granted') throw new Error('Orientation permission denied');
      }
      if (typeof window.DeviceMotionEvent?.requestPermission === 'function') {
        const result = await window.DeviceMotionEvent.requestPermission();
        if (result !== 'granted') throw new Error('Motion permission denied');
      }
      addEventListener('deviceorientation', onOrientation, true);
      addEventListener('devicemotion', onMotion, true);
      state.motionEnabled = true;
      $('motionStatus').textContent = 'Motion: live';
      $('motionBtn').textContent = 'MOTION ON';
      toast('Motion guidance enabled');
    } catch (err) {
      console.error(err);
      toast('Motion permission was not granted.');
    }
  }
  $('motionBtn').addEventListener('click', enableMotion);

  function onOrientation(e) {
    // gamma approximates left/right roll in portrait; clamp to useful guide range.
    const g = Number.isFinite(e.gamma) ? e.gamma : 0;
    state.horizon = Math.max(-30, Math.min(30, g));
  }
  function onMotion(e) {
    const a = e.accelerationIncludingGravity || e.acceleration || {};
    const mag = Math.hypot(a.x || 0, a.y || 0, a.z || 0);
    const delta = Math.abs(mag - state.lastMotionMag);
    state.lastMotionMag = mag;
    const instant = Math.max(0, Math.min(100, 100 - delta * 18));
    state.smoothness = state.smoothness * .88 + instant * .12;
  }

  async function ensureDetector() {
    if (state.detector || state.detectorLoading) return;
    state.detectorLoading = true;
    $('aiStatus').textContent = 'AI model: loading in background…';

    try {
      if (!window.tf) {
        await loadScript('https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.min.js', 8000);
      }
      if (!window.cocoSsd) {
        await loadScript('https://cdn.jsdelivr.net/npm/@tensorflow-models/coco-ssd@2.2.3/dist/coco-ssd.min.js', 8000);
      }

      if (!window.tf || !window.cocoSsd) throw new Error('AI libraries unavailable');

      state.detector = await Promise.race([
        window.cocoSsd.load({ base: 'lite_mobilenet_v2' }),
        new Promise((_, reject) => setTimeout(() => reject(new Error('AI model timeout')), 12000))
      ]);

      $('aiStatus').textContent = 'AI model: on-device';
    } catch (err) {
      console.warn('AI detector fallback:', err);
      state.detector = null;
      state.detections = [];
      $('aiStatus').textContent = 'AI model: basic analysis';
      toast('AI model unavailable — camera works in basic mode.');
    } finally {
      state.detectorLoading = false;
    }
  }

  async function analyzeFrame(now) {
    if (!state.stream || video.readyState < 2 || now - state.lastAnalysis < 650) return;
    state.lastAnalysis = now;
    const w = 192, h = Math.round(192 * (video.videoHeight || 9) / (video.videoWidth || 16));
    analysisCanvas.width = w; analysisCanvas.height = Math.max(108, Math.min(h, 192));
    analysisCtx.drawImage(video, 0, 0, analysisCanvas.width, analysisCanvas.height);
    const data = analysisCtx.getImageData(0, 0, analysisCanvas.width, analysisCanvas.height).data;
    let total = 0, highlights = 0, pixels = data.length / 4;
    for (let i=0; i<data.length; i+=4) {
      const l = .2126*data[i] + .7152*data[i+1] + .0722*data[i+2];
      total += l;
      if (l > 242) highlights++;
    }
    state.averageLuma = total / pixels;
    state.highlightFraction = highlights / pixels;

    if (state.detector) {
      try {
        const preds = await state.detector.detect(video, 6, .45);
        state.detections = preds.filter(p => ['person','car','dog','cat','bicycle','motorcycle','cell phone'].includes(p.class)).sort((a,b)=>b.score-a.score).slice(0,3);
      } catch (err) { console.warn(err); }
    }
  }

  function normRectFromDetection(det) {
    if (!det || !video.videoWidth || !video.videoHeight) return null;
    const [x,y,w,h] = det.bbox;
    return { left:x/video.videoWidth, top:y/video.videoHeight, right:(x+w)/video.videoWidth, bottom:(y+h)/video.videoHeight,
      get centerX(){return (this.left+this.right)/2}, get height(){return this.bottom-this.top} };
  }

  function guidanceInput() {
    return {
      subjectRect: normRectFromDetection(state.detections[0]),
      averageLuma: state.averageLuma,
      highlightFraction: state.highlightFraction,
      horizonDegrees: state.motionEnabled ? state.horizon : 0,
      smoothness: state.motionEnabled ? state.smoothness : 100
    };
  }

  function recommendation(input) {
    if (Math.abs(input.horizonDegrees) > 3) return 'Level the horizon.';
    if (input.averageLuma < 58) return 'Subject is dark — move toward softer light.';
    if (input.highlightFraction > .16) return 'Highlights are clipping — reduce exposure.';
    if (input.smoothness < 58) return 'Slow down the camera movement.';
    const r = input.subjectRect;
    if (!r) return state.detector ? 'Find a clear subject or hold the camera steady.' : 'Keep the camera stable and use the grid for composition.';
    if (r.top < .035) return 'Give the subject a little more headroom.';
    if (r.centerX < .30) return 'Move framing slightly right.';
    if (r.centerX > .70) return 'Move framing slightly left.';
    if (r.height < .24) return 'Move a little closer.';
    return 'Framing looks good — keep the movement smooth.';
  }

  function compositionConsistency(input) {
    let score = 100;
    score -= Math.min(25, Math.abs(input.horizonDegrees)*5);
    score -= Math.min(20, (100-input.smoothness)*.25);
    if (input.subjectRect) {
      const r = input.subjectRect;
      const nearest = Math.min(Math.abs(r.centerX-1/3), Math.abs(r.centerX-2/3));
      score -= Math.min(20, nearest*55);
    } else score -= 20;
    return Math.max(0, Math.min(100, Math.round(score)));
  }

  function shotReady(tech, r, horizon, smoothness) {
    if (!state.coachEnabled || !r) return false;
    if (Math.abs(horizon)>3 || smoothness<60) return false;
    switch (tech) {
      case 'CLOSE_UP': return r.height>.32 && r.centerX>=.34 && r.centerX<=.66;
      case 'WIDE': return r.height>=.18 && r.height<=.55;
      case 'MEDIUM': case 'HERO': return r.height>=.32 && r.height<=.78 && r.centerX>=.25 && r.centerX<=.75;
      case 'TRACKING': case 'DOLLY': case 'ORBIT': return r.centerX>=.24 && r.centerX<=.76;
      default: return false;
    }
  }

  function drawOverlay() {
    const rect = overlay.getBoundingClientRect();
    const w = rect.width, h = rect.height;
    ctx.clearRect(0,0,w,h);
    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(255,255,255,.22)';
    [1/3,2/3].forEach(f => { ctx.beginPath();ctx.moveTo(w*f,0);ctx.lineTo(w*f,h);ctx.stroke(); ctx.beginPath();ctx.moveTo(0,h*f);ctx.lineTo(w,h*f);ctx.stroke(); });

    const input = guidanceInput();
    const r = input.subjectRect;
    const ready = shotReady(state.selectedTechnique, r, input.horizonDegrees, input.smoothness);
    const color = ready ? '#7dffaa' : '#64e7ff';

    if (r) {
      ctx.lineWidth = 2; ctx.strokeStyle = color;
      ctx.strokeRect(r.left*w, r.top*h, (r.right-r.left)*w, (r.bottom-r.top)*h);
      ctx.beginPath(); ctx.arc(r.centerX*w, ((r.top+r.bottom)/2)*h, 4, 0, Math.PI*2); ctx.stroke();
    }

    const angle = (input.horizonDegrees*Math.PI)/180;
    const half = w*.22, cx=w/2, cy=h/2;
    const dx=Math.cos(angle)*half, dy=Math.sin(angle)*half;
    ctx.strokeStyle='rgba(255,255,255,.58)'; ctx.lineWidth=2;
    ctx.beginPath();ctx.moveTo(cx-dx,cy-dy);ctx.lineTo(cx+dx,cy+dy);ctx.stroke();

    if (state.coachEnabled) drawCoachGuide(state.selectedTechnique, color, w, h);

    const score = compositionConsistency(input);
    $('qualityPill').textContent = `${state.stream ? 'LIVE' : '—'} • C ${score}%`;
    $('guidanceText').textContent = ready ? 'SHOT READY ✓' : recommendation(input);
    $('guidance').classList.toggle('ready', ready);
  }

  function drawCoachGuide(tech, color, w, h) {
    ctx.strokeStyle=color; ctx.lineWidth=4; ctx.globalAlpha=.8;
    const cx=w/2, cy=h*.62;
    if (tech==='DOLLY') { line(cx,h*.78,cx,h*.45); circle(cx,h*.45,8); }
    else if (tech==='TRACKING') { line(w*.2,cy,w*.8,cy); circle(w*.8,cy,8); }
    else if (tech==='ORBIT') { ctx.beginPath();ctx.arc(cx,h*.54,w*.25,Math.PI*1.1,Math.PI*2.45);ctx.stroke(); }
    else {
      const tw = tech==='WIDE'?.62:tech==='CLOSE_UP'?.34:.46;
      const th = tech==='WIDE'?.60:tech==='CLOSE_UP'?.34:.50;
      ctx.lineWidth=2;ctx.strokeRect(w*(.5-tw/2),h*(.48-th/2),w*tw,h*th);
    }
    ctx.globalAlpha=1;
    function line(x1,y1,x2,y2){ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();}
    function circle(x,y,r){ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.stroke();}
  }

  function frame(now) {
    analyzeFrame(now);
    drawOverlay();
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  function directorPlan(prompt) {
    const p = prompt.toLowerCase();
    if (p.includes('horror') || p.includes('scary')) return {shot:'Off-center medium-wide shot',height:'Chest to eye level',subject:'Place subject on one third with negative space',movement:'Slow creeping push-in',lens:'1x / wide if the environment matters',light:'Keep the face readable but let the background fall darker',coach:'TRACKING'};
    if (p.includes('product')) return {shot:'Clean product hero shot',height:'Level with the product center',subject:'Center or precise third',movement:'Slow orbit or push-in',lens:'2x / telephoto if available',light:'Soft side light; protect highlights',coach:'ORBIT'};
    if (p.includes('car')) return {shot:'Low three-quarter hero shot',height:'Near wheel / bumper height',subject:'Car on lower-middle frame',movement:'Slow tracking or arc',lens:'1x to 2x depending on space',light:'Use side/back light for body reflections',coach:'TRACKING'};
    if (p.includes('dialog') || p.includes('conversation')) return {shot:'Over-the-shoulder medium close-up',height:'Near subject eye level',subject:'Eyes near upper third; preserve look room',movement:'Mostly locked, subtle drift only',lens:'2x if space permits',light:'Keep both face and eyeline side consistent',coach:'MEDIUM'};
    if (p.includes('sci') || p.includes('future')) return {shot:'Symmetric low-angle medium shot',height:'Slightly below chest level',subject:'Centered with architectural lines',movement:'Controlled push-in',lens:'1x or 2x',light:'Cool practicals with a controlled edge light',coach:'HERO'};
    return {shot:'Low-angle medium hero shot',height:'Approximately waist level',subject:'Center-left with clean headroom',movement:'Slow push-in',lens:'2x / telephoto if available',light:'Stronger side or back light; protect skin highlights',coach:'HERO'};
  }

  $('buildPlanBtn').addEventListener('click', () => {
    const p = directorPlan($('directorPrompt').value);
    const items=[['SHOT',p.shot],['CAMERA HEIGHT',p.height],['SUBJECT',p.subject],['MOVEMENT',p.movement],['LENS',p.lens],['LIGHT',p.light]];
    $('directorPlan').innerHTML = items.map(([l,v])=>`<div class="card"><div class="card-label">${l}</div><div class="card-title">${escapeHtml(v)}</div></div>`).join('') + `<button class="primary wide" id="directorCoachBtn">Open Shot Coach</button>`;
    $('directorCoachBtn').onclick=()=>{ selectTechnique(p.coach); switchView('coachView'); };
  });

  function renderCoach() {
    $('coachGrid').innerHTML = Object.entries(techniques).map(([key,t])=>`<button class="tech-card ${key===state.selectedTechnique?'active':''}" data-tech="${key}"><strong>${t.label}</strong><small>${t.description}</small></button>`).join('');
    document.querySelectorAll('[data-tech]').forEach(b=>b.onclick=()=>selectTechnique(b.dataset.tech));
    const t=techniques[state.selectedTechnique]; $('coachTitle').textContent=t.label; $('coachDescription').textContent=t.description;
  }
  function renderCoachChips() {
    $('coachChips').classList.toggle('hidden', !state.coachEnabled);
    $('coachChips').innerHTML = Object.entries(techniques).slice(0,5).map(([key,t])=>`<button class="${key===state.selectedTechnique?'active':''}" data-chip="${key}">${t.label}</button>`).join('');
    document.querySelectorAll('[data-chip]').forEach(b=>b.onclick=()=>selectTechnique(b.dataset.chip));
  }
  function selectTechnique(key) {
    state.selectedTechnique = key in techniques ? key : 'HERO';
    renderCoach(); renderCoachChips();
  }
  renderCoach(); renderCoachChips();

  $('openCoachCameraBtn').addEventListener('click', () => { state.coachEnabled=true; renderCoachChips(); switchView('cameraView'); if(!state.stream) startCamera(); });

  function storyboard(scene) {
    const s=scene.toLowerCase();
    const shots=[
      {number:1,name:'Wide Establishing',intent:'Establish geography and movement before the action begins.',tech:'WIDE'},
      {number:2,name:'Medium Action',intent:'Follow the main action while keeping body language readable.',tech:'MEDIUM'}
    ];
    if (s.includes('car') || s.includes('drive')) shots.push({number:3,name:'Low Car Tracking',intent:'Add motion and visual energy around the vehicle.',tech:'TRACKING'});
    else shots.push({number:3,name:'Close-Up Detail',intent:'Capture the emotional or narrative detail.',tech:'CLOSE_UP'});
    shots.push({number:4,name:'Hero Push-In',intent:'Finish with a deliberate emphasis shot.',tech:'DOLLY'});
    return shots;
  }
  $('storyboardBtn').addEventListener('click',()=>{
    const shots=storyboard($('scenePrompt').value);
    $('storyboardList').innerHTML=shots.map(s=>`<div class="card"><div class="card-label">SHOT ${String(s.number).padStart(2,'0')}</div><div class="card-title">${s.name}</div><p>${s.intent}</p><button class="small-btn" data-story-tech="${s.tech}">Coach this shot</button></div>`).join('');
    document.querySelectorAll('[data-story-tech]').forEach(b=>b.onclick=()=>{selectTechnique(b.dataset.storyTech);state.coachEnabled=true;renderCoachChips();switchView('cameraView');if(!state.stream)startCamera();});
  });

  function saveProjects() { localStorage.setItem('aidc.projects', JSON.stringify(state.projects)); localStorage.setItem('aidc.activeProjectId', state.activeProjectId || ''); updateProjectStatus(); }
  function updateProjectStatus() { const p=state.projects.find(x=>x.id===state.activeProjectId); $('projectStatus').textContent=p?`PROJECT • ${p.name.toUpperCase()}`:'NO PROJECT'; }
  function renderProjects() {
    if (!state.projects.length) { $('projectList').innerHTML='<div class="card"><div class="card-title">No projects yet</div><p>Create one to attach recording metadata to it.</p></div>'; return; }
    $('projectList').innerHTML=state.projects.map(p=>`<div class="card project-row ${p.id===state.activeProjectId?'active':''}"><div><div class="card-title">${escapeHtml(p.name)}</div><div class="project-meta">${p.shots?.length||0} shots • ${p.id===state.activeProjectId?'ACTIVE':'tap to activate'}</div></div><button class="small-btn" data-project="${p.id}">${p.id===state.activeProjectId?'Active':'Activate'}</button></div>`).join('');
    document.querySelectorAll('[data-project]').forEach(b=>b.onclick=()=>{state.activeProjectId=b.dataset.project;saveProjects();renderProjects();});
  }
  $('createProjectBtn').addEventListener('click',()=>{
    const name=$('projectName').value.trim(); if(!name) return toast('Enter a project name.');
    const id=String(Date.now()); state.projects.unshift({id,name,createdAt:new Date().toISOString(),shots:[]}); state.activeProjectId=id; $('projectName').value=''; saveProjects();renderProjects();toast('Project created');
  });
  function saveShotMetadata(durationMs) {
    const p=state.projects.find(x=>x.id===state.activeProjectId); if(!p) return;
    const input=guidanceInput();
    p.shots = p.shots || [];
    p.shots.unshift({id:String(Date.now()),createdAt:new Date().toISOString(),type:state.coachEnabled?techniques[state.selectedTechnique].label:'Free Camera',durationMs:Math.round(durationMs),lens:state.facingMode==='environment'?'Rear':'Front',recommendation:recommendation(input),smoothness:Math.round(input.smoothness),composition:compositionConsistency(input)});
    saveProjects();
  }
  updateProjectStatus();

  function escapeHtml(s='') { return s.replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#039;','"':'&quot;'}[c])); }

  if ('serviceWorker' in navigator) {
    addEventListener('load', async () => {
      try {
        const registration = await navigator.serviceWorker.register('./sw.js?v=2');
        await registration.update();
      } catch (err) {
        console.warn('Service worker registration failed:', err);
      }
    });
  }

  document.addEventListener('visibilitychange',()=>{
    // iOS can re-prompt after abrupt media suspension; proactively stop tracks when hidden.
    if (document.hidden && state.mediaRecorder?.state!=='recording' && state.stream) {
      stopCamera();
      $('startBtn').textContent='RESUME CAMERA';
    }
  });
})();
