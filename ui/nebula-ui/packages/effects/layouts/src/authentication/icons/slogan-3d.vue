<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { preferences, usePreferences } from '@nebula/preferences';

import * as THREE from 'three';

defineOptions({
  name: 'AuthenticationSlogan3D',
});

const { isDark } = usePreferences();

/** 容器与降级标记 */
const containerRef = ref<HTMLDivElement>();
/** WebGL 不可用时回退到默认插槽（SVG 插画） */
const webglSupported = ref(true);

/**
 * @zh_CN 读取当前主题色（设计系统中 `--primary` 以 HSL 分量形式存储，
 * 例如 `212 100% 45%`），转换为 three.js 颜色，使 3D 场景随主题联动。
 */
function readPrimaryColor(): THREE.Color {
  const color = new THREE.Color();
  const raw =
    typeof window === 'undefined'
      ? ''
      : getComputedStyle(document.documentElement)
          .getPropertyValue('--primary')
          .trim();
  if (raw) {
    const [h, s, l] = raw.split(/\s+/);
    color.setStyle(`hsl(${h}, ${s}, ${l})`);
  } else {
    color.setStyle('hsl(212, 100%, 45%)');
  }
  return color;
}

// ---- three.js 运行时对象（不需要响应式，置于组件作用域内即可） ----
let renderer: THREE.WebGLRenderer | undefined;
let scene: THREE.Scene | undefined;
let camera: THREE.PerspectiveCamera | undefined;
let cubeGroup: THREE.Group | undefined;
let particles: THREE.Points | undefined;
let resizeObserver: ResizeObserver | undefined;
let frameId = 0;
const clock = new THREE.Clock();
const disposables: { dispose: () => void }[] = [];
const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** 缓存每个立方体的初始位置 / 相位，用于浮动动画 */
const cubeMeta: { baseY: number; phase: number; speed: number }[] = [];

/**
 * @zh_CN 构建一簇等距错落的玻璃质感立方体，呼应项目原有的「数据魔方」插画。
 */
function buildCubes(primary: THREE.Color) {
  cubeGroup = new THREE.Group();
  cubeMeta.length = 0;

  // 错落的「楼层」布局：3x3 底座，高度各异，营造数据堆栈感
  const layout: [number, number, number][] = [
    [-1, 0, -1],
    [0, 0, -1],
    [1, 0, -1],
    [-1, 0, 0],
    [0, 1, 0],
    [1, 0, 0],
    [-1, 0, 1],
    [0, 0, 1],
    [1, 0, 1],
    [0, 2, 0],
  ];

  const gap = 1.25;
  for (const [x, y, z] of layout) {
    const geometry = new THREE.BoxGeometry(1, 1, 1);
    const faceMaterial = new THREE.MeshBasicMaterial({
      color: primary,
      transparent: true,
      opacity: 0.12,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(geometry, faceMaterial);

    const edgesGeometry = new THREE.EdgesGeometry(geometry);
    const edgeMaterial = new THREE.LineBasicMaterial({
      color: primary,
      transparent: true,
      opacity: 0.9,
    });
    const edges = new THREE.LineSegments(edgesGeometry, edgeMaterial);
    mesh.add(edges);

    mesh.position.set(x * gap, y * gap, z * gap);
    cubeGroup.add(mesh);
    cubeMeta.push({
      baseY: mesh.position.y,
      phase: Math.random() * Math.PI * 2,
      speed: 0.6 + Math.random() * 0.6,
    });

    disposables.push(geometry, faceMaterial, edgesGeometry, edgeMaterial);
  }

  // 整体微微倾斜，呈现等距视角
  cubeGroup.rotation.x = -0.18;
  scene?.add(cubeGroup);
}

/**
 * @zh_CN 漂浮的粒子，增强空间纵深与科技氛围。
 */
function buildParticles(primary: THREE.Color) {
  const count = 120;
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 16;
    positions[i * 3 + 1] = (Math.random() - 0.5) * 10;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 16;
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  const material = new THREE.PointsMaterial({
    color: primary,
    size: 0.08,
    transparent: true,
    opacity: 0.6,
    depthWrite: false,
    sizeAttenuation: true,
  });
  particles = new THREE.Points(geometry, material);
  scene?.add(particles);
  disposables.push(geometry, material);
}

/**
 * @zh_CN 主题（亮/暗 + 主色）变化时刷新场景配色。
 */
function applyTheme() {
  const primary = readPrimaryColor();
  cubeGroup?.traverse((object) => {
    if (
      object instanceof THREE.Mesh ||
      object instanceof THREE.LineSegments ||
      object instanceof THREE.Points
    ) {
      const material = object.material as
        | THREE.LineBasicMaterial
        | THREE.MeshBasicMaterial;
      material.color.copy(primary);
    }
  });
  if (particles) {
    (particles.material as THREE.PointsMaterial).color.copy(primary);
  }
  if (!frameId) {
    renderFrame();
  }
}

/** 渲染单帧（用于静态 / 减弱动效模式与尺寸变化时） */
function renderFrame() {
  if (renderer && scene && camera) {
    renderer.render(scene, camera);
  }
}

function animate() {
  frameId = requestAnimationFrame(animate);
  const elapsed = clock.getElapsedTime();

  if (cubeGroup) {
    cubeGroup.rotation.y = elapsed * 0.25;
    cubeGroup.children.forEach((child, index) => {
      const meta = cubeMeta[index];
      if (meta) {
        child.position.y =
          meta.baseY + Math.sin(elapsed * meta.speed + meta.phase) * 0.12;
      }
    });
  }
  if (particles) {
    particles.rotation.y = -elapsed * 0.04;
  }
  renderFrame();
}

function resize() {
  const container = containerRef.value;
  if (!container || !renderer || !camera) {
    return;
  }
  const { clientWidth: width, clientHeight: height } = container;
  if (width === 0 || height === 0) {
    return;
  }
  renderer.setSize(width, height, false);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  renderFrame();
}

function init() {
  const container = containerRef.value;
  if (!container) {
    return;
  }

  try {
    renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      powerPreference: 'high-performance',
    });
  } catch {
    webglSupported.value = false;
    return;
  }
  if (!renderer.getContext()) {
    webglSupported.value = false;
    return;
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setClearColor(0x00_00_00, 0);
  container.append(renderer.domElement);
  renderer.domElement.style.width = '100%';
  renderer.domElement.style.height = '100%';
  renderer.domElement.style.display = 'block';

  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
  camera.position.set(5.5, 4.5, 7.5);
  camera.lookAt(0, 0.5, 0);

  const primary = readPrimaryColor();
  buildCubes(primary);
  buildParticles(primary);

  resize();

  resizeObserver = new ResizeObserver(() => resize());
  resizeObserver.observe(container);

  if (reduceMotion) {
    renderFrame();
  } else {
    clock.start();
    animate();
  }
}

onMounted(() => {
  init();
});

onBeforeUnmount(() => {
  if (frameId) {
    cancelAnimationFrame(frameId);
    frameId = 0;
  }
  resizeObserver?.disconnect();
  resizeObserver = undefined;

  for (const item of disposables) {
    item.dispose();
  }
  disposables.length = 0;

  renderer?.domElement.remove();
  renderer?.dispose();
  renderer = undefined;
  scene = undefined;
  camera = undefined;
  cubeGroup = undefined;
  particles = undefined;
});

// 主题模式与主色切换时同步场景配色
watch([isDark, () => preferences.theme.colorPrimary], () => {
  // 等待 CSS 变量更新到 DOM 后再读取
  requestAnimationFrame(applyTheme);
});
</script>

<template>
  <div ref="containerRef" class="slogan-3d size-full">
    <slot v-if="!webglSupported"></slot>
  </div>
</template>

<style scoped>
.slogan-3d {
  position: relative;
  overflow: hidden;
}
</style>
