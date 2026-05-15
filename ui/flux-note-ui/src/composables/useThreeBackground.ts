import { onMounted, onUnmounted, watch, type Ref } from 'vue'
import * as THREE from 'three'

const THEME = {
  light: {
    bg:      0xf1f5f9,
    face:    0xffffff,
    faceOp:  0.08,
    rim:     0x64748b,
    tick:    0x475569,
    hourCol: 0x1e293b,
    minCol:  0x334155,
    secCol:  0xef4444,
    center:  0x1e293b,
  },
  dark: {
    bg:      0x080d18,
    face:    0x0f172a,
    faceOp:  0.55,
    rim:     0x334155,
    tick:    0x475569,
    hourCol: 0xe2e8f0,
    minCol:  0x94a3b8,
    secCol:  0xf87171,
    center:  0xe2e8f0,
  },
}

function makeLine(x1: number, y1: number, x2: number, y2: number, color: number, opacity = 1): THREE.Line {
  const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(x1, y1, 0), new THREE.Vector3(x2, y2, 0)])
  const m = new THREE.LineBasicMaterial({ color, transparent: opacity < 1, opacity })
  return new THREE.Line(g, m)
}

function makeCircle(radius: number, color: number, opacity = 1): THREE.Line {
  const pts: THREE.Vector3[] = []
  for (let i = 0; i <= 128; i++) {
    const a = (i / 128) * Math.PI * 2
    pts.push(new THREE.Vector3(Math.cos(a) * radius, Math.sin(a) * radius, 0))
  }
  const g = new THREE.BufferGeometry().setFromPoints(pts)
  const m = new THREE.LineBasicMaterial({ color, transparent: opacity < 1, opacity })
  return new THREE.Line(g, m)
}

function makeHand(length: number, width: number, color: number): { mesh: THREE.Mesh; mat: THREE.MeshBasicMaterial } {
  const g = new THREE.PlaneGeometry(width, length)
  g.translate(0, length / 2, 0)
  const mat = new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide })
  return { mesh: new THREE.Mesh(g, mat), mat }
}

export function useThreeBackground(canvasRef: Ref<HTMLCanvasElement | null>, isDark: Ref<boolean>) {
  let renderer: THREE.WebGLRenderer | null = null
  let animId: number | null = null

  const scene  = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 500)
  camera.position.set(0, 0, 90)
  camera.lookAt(0, 0, 0)

  const R = 18  // smaller clock
  const clockGroup = new THREE.Group()
  // position: left side, vertically centered
  // will be updated in resize() to stay at ~20% from left
  scene.add(clockGroup)

  // face disc
  const faceGeo = new THREE.CircleGeometry(R, 128)
  const faceMat = new THREE.MeshBasicMaterial({ transparent: true, side: THREE.DoubleSide })
  clockGroup.add(new THREE.Mesh(faceGeo, faceMat))

  // rim + inner ring
  const rimLine   = makeCircle(R, 0xffffff)
  const innerRim  = makeCircle(R * 0.92, 0xffffff, 0.15)
  clockGroup.add(rimLine, innerRim)

  // tick marks
  const tickLines: THREE.Line[] = []
  for (let i = 0; i < 60; i++) {
    const a   = (i / 60) * Math.PI * 2 - Math.PI / 2
    const isH = i % 5 === 0
    const r0  = isH ? R * 0.82 : R * 0.88
    const line = makeLine(Math.cos(a) * r0, Math.sin(a) * r0, Math.cos(a) * R * 0.96, Math.sin(a) * R * 0.96, 0xffffff, isH ? 0.9 : 0.35)
    clockGroup.add(line)
    tickLines.push(line)
  }

  // hour numbers via canvas texture
  const numMeshes: THREE.Mesh[] = []
  for (let h = 1; h <= 12; h++) {
    const a  = (h / 12) * Math.PI * 2 - Math.PI / 2
    const cv = document.createElement('canvas')
    cv.width = 64; cv.height = 64
    const ctx = cv.getContext('2d')!
    ctx.font = 'bold 36px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillStyle = '#ffffff'
    ctx.fillText(String(h), 32, 32)
    const tex  = new THREE.CanvasTexture(cv)
    const mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(5.5, 5.5),
      new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }),
    )
    mesh.position.set(Math.cos(a) * R * 0.70, Math.sin(a) * R * 0.70, 0.1)
    clockGroup.add(mesh)
    numMeshes.push(mesh)
  }

  // hands
  const { mesh: hourMesh, mat: hourMat } = makeHand(R * 0.50, 1.6, 0xffffff)
  const { mesh: minMesh,  mat: minMat  } = makeHand(R * 0.72, 1.1, 0xffffff)
  const { mesh: secMesh,  mat: secMat  } = makeHand(R * 0.82, 0.5, 0xff4444)
  const { mesh: secTail               } = makeHand(R * 0.20, 0.5, 0xff4444)
  secTail.rotation.z = Math.PI
  hourMesh.position.z = 0.3
  minMesh.position.z  = 0.4
  secMesh.position.z  = 0.5
  secTail.position.z  = 0.5
  clockGroup.add(hourMesh, minMesh, secMesh, secTail)

  // center dot
  const dotMat = new THREE.MeshBasicMaterial()
  clockGroup.add(new THREE.Mesh(new THREE.CircleGeometry(1.2, 32), dotMat))

  // ── theme ─────────────────────────────────────────────────
  function applyTheme(dark: boolean) {
    const T = dark ? THEME.dark : THEME.light
    scene.background = new THREE.Color(T.bg)
    faceMat.color    = new THREE.Color(T.face)
    faceMat.opacity  = T.faceOp
    ;(rimLine.material as THREE.LineBasicMaterial).color = new THREE.Color(T.rim)
    hourMat.color    = new THREE.Color(T.hourCol)
    minMat.color     = new THREE.Color(T.minCol)
    secMat.color     = new THREE.Color(T.secCol)
    dotMat.color     = new THREE.Color(T.center)
    tickLines.forEach(l => { (l.material as THREE.LineBasicMaterial).color = new THREE.Color(T.tick) })
    numMeshes.forEach((m, idx) => {
      const cv  = ((m.material as THREE.MeshBasicMaterial).map as THREE.CanvasTexture).image as HTMLCanvasElement
      const ctx = cv.getContext('2d')!
      ctx.clearRect(0, 0, 64, 64)
      ctx.font = 'bold 36px system-ui, sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillStyle = dark ? '#94a3b8' : '#334155'
      ctx.fillText(String(idx + 1), 32, 32)
      ;((m.material as THREE.MeshBasicMaterial).map as THREE.CanvasTexture).needsUpdate = true
    })
  }

  // ── resize ────────────────────────────────────────────────
  function resize() {
    if (!renderer || !canvasRef.value) return
    const { clientWidth: w, clientHeight: h } = canvasRef.value
    renderer.setSize(w, h, false)
    camera.aspect = w / h
    camera.updateProjectionMatrix()

    // keep clock at left ~18% of viewport, vertically centered
    // visible half-width at z=0: tan(fov/2) * camZ * aspect
    const fovRad   = (55 * Math.PI) / 180
    const halfH    = Math.tan(fovRad / 2) * 90          // world units half-height
    const halfW    = halfH * camera.aspect
    clockGroup.position.x = -halfW * 0.62               // ~19% from left edge
    clockGroup.position.y = 0
  }
  const ro = new ResizeObserver(resize)

  // ── animate ───────────────────────────────────────────────
  function animate() {
    animId = requestAnimationFrame(animate)
    const now  = new Date()
    const sec  = now.getSeconds() + now.getMilliseconds() / 1000
    const min  = now.getMinutes() + sec / 60
    const hour = (now.getHours() % 12) + min / 60
    hourMesh.rotation.z  = -(hour / 12) * Math.PI * 2
    minMesh.rotation.z   = -(min  / 60) * Math.PI * 2
    secMesh.rotation.z   = -(sec  / 60) * Math.PI * 2
    secTail.rotation.z   = secMesh.rotation.z
    renderer?.render(scene, camera)
  }

  onMounted(() => {
    if (!canvasRef.value) return
    renderer = new THREE.WebGLRenderer({ canvas: canvasRef.value, antialias: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
    applyTheme(isDark.value)
    resize()
    ro.observe(canvasRef.value)
    animate()
  })

  watch(isDark, applyTheme)

  onUnmounted(() => {
    if (animId !== null) cancelAnimationFrame(animId)
    ro.disconnect()
    renderer?.dispose()
  })
}
