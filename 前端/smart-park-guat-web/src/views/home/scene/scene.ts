import type { SceneInput } from './types';
export type { SceneInput } from './types';
interface Rack {
  id: string;
  group: THREE.Group;
  roof: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshStandardMaterial>;
  highlight: THREE.LineSegments<THREE.EdgesGeometry, THREE.LineBasicMaterial>;
  alarmVisual: THREE.Group;
  alarmEdge: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  alarmOutline: THREE.LineSegments<THREE.EdgesGeometry, THREE.LineBasicMaterial>;
  alarmFill: THREE.Mesh<THREE.BoxGeometry, THREE.MeshBasicMaterial>;
  zone: string;
  door: THREE.Group;
  doorOpen: boolean;
  doorAngle: number;
  stateMat: THREE.MeshBasicMaterial;
  beacon: THREE.Mesh;
  strip: THREE.Mesh;
  alarm: SceneInput['devices'][number]['alarm'];
}
import { gsap } from 'gsap';
import * as THREE from './vendor/three.module.js';
import { OrbitControls } from './vendor/OrbitControls.js';
import { mockData as demoData } from '../demo/mockData.js';
import { createPatrol } from './patrol.ts';
import { icon } from './icons.ts';
import { cameras } from './cameras.ts';
import { airConditioners, demoFacilities } from './facilities.ts';
import { topologyNodes as demoTopologyNodes } from '../demo/topologyData.ts';
import { alarmLevels } from '@/utils/alarm-levels';

function renderable(
  parent: THREE.Object3D,
  name: string,
):
  | THREE.Mesh<THREE.BufferGeometry, THREE.Material>
  | THREE.LineSegments<THREE.BufferGeometry, THREE.Material> {
  const child = parent.getObjectByName(name);
  if (!(child instanceof THREE.Mesh || child instanceof THREE.LineSegments))
    throw new Error(`三维模型缺少渲染节点：${name}`);
  return child;
}
function child(parent: THREE.Object3D, name: string): THREE.Object3D {
  const node = parent.getObjectByName(name);
  if (!node) throw new Error(`三维模型缺少节点：${name}`);
  return node;
}
export function createRoom(
  root: HTMLElement,
  options: {
    data?: SceneInput;
    onCameraPick?: (id: string) => void;
    onDevicePick?: (id: string) => void;
    onDoorClick?: (id: string) => void;
    onAlarmInspect?: (id: string) => void;
  } = {},
) {
  const d: {
    devices: SceneInput['devices'];
    zones: SceneInput['zones'];
    room: typeof demoData.room;
  } = options.data
    ? { ...demoData, devices: options.data.devices, zones: options.data.zones }
    : demoData;
  let topologyNodes: SceneInput['inventory'] = options.data?.inventory || demoTopologyNodes;
  const local = (id: string) => {
    const element = root.querySelector<HTMLElement>(`[id="${id}"]`);
    if (!element) throw new Error(`三维场景缺少容器：${id}`);
    return element;
  };
  const abort = new AbortController();
  const listen = <K extends keyof HTMLElementEventMap>(
    target: HTMLElement,
    event: K,
    handler: (event: HTMLElementEventMap[K]) => void,
  ) => target.addEventListener(event, handler, { signal: abort.signal });
  let frame = 0,
    disposed = false;
  const host = local('sceneCanvas'),
    viewport = local('roomViewport');
  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 1.75));
  renderer.setClearColor(0x000000, 0);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.16;
  renderer.domElement.setAttribute(
    'aria-label',
    '程序化三维机房：金属模块化机柜、透明柜门、玻璃立面与浅灰架空地板',
  );
  host.appendChild(renderer.domElement);
  const scene = new THREE.Scene(),
    camera = new THREE.PerspectiveCamera(35, 1, 0.1, 120);
  const overviewPosition = new THREE.Vector3(0, 22.4, 20.3),
    target = new THREE.Vector3(0, -1.3, 0);
  camera.position.copy(overviewPosition);
  camera.lookAt(target);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.target.copy(target);
  controls.enabled = false;
  controls.enablePan = false;
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.minDistance = 32;
  controls.maxDistance = 40;
  controls.minPolarAngle = 0.55;
  controls.maxPolarAngle = 1.0;
  controls.minAzimuthAngle = -0.5;
  controls.maxAzimuthAngle = 0.5;
  controls.rotateSpeed = 0.45;
  controls.zoomSpeed = 0.55;
  controls.update();
  scene.add(new THREE.HemisphereLight(0xe4efff, 0x465065, 2.2));
  scene.add(new THREE.AmbientLight(0xffffff, 0.45));
  const sun = new THREE.DirectionalLight(0xfff9ef, 3.5);
  sun.position.set(-7, 19, 12);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  sun.shadow.camera.left = -14;
  sun.shadow.camera.right = 14;
  sun.shadow.camera.top = 14;
  sun.shadow.camera.bottom = -14;
  sun.shadow.camera.far = 60;
  sun.shadow.bias = -0.0005;
  sun.shadow.normalBias = 0.025;
  sun.shadow.radius = 3;
  scene.add(sun);
  const fill = new THREE.DirectionalLight(0xc7d9ee, 1.6);
  fill.position.set(10, 10, -8);
  scene.add(fill);
  const blueLight = new THREE.PointLight(0x369cff, 18, 25, 2);
  blueLight.position.set(0, 5, 8);
  scene.add(blueLight);
  const room = new THREE.Group();
  scene.add(room);
  const geo = new Map<string, THREE.BoxGeometry>();
  function boxGeometry(w: number, h: number, z: number) {
    const key = `${w},${h},${z}`;
    if (!geo.has(key)) geo.set(key, new THREE.BoxGeometry(w, h, z));
    return geo.get(key)!;
  }
  function mat(color: THREE.ColorRepresentation, roughness = 0.65, metalness = 0.1) {
    return new THREE.MeshStandardMaterial({ color, roughness, metalness });
  }
  const black = mat(0x171b22, 0.5, 0.55),
    dark = mat(0x292f37, 0.58, 0.5),
    silver = mat(0xa0a8ae, 0.48, 0.62),
    wall = mat(0x64717f, 0.7, 0.2),
    trim = mat(0x344453, 0.38, 0.7),
    gold = mat(0x8a783c, 0.6, 0.5);
  const blue = new THREE.MeshBasicMaterial({ color: 0x45baff }),
    dimBlue = new THREE.MeshBasicMaterial({ color: 0x1570c5 });
  function box(
    w: number,
    h: number,
    z: number,
    x: number,
    y: number,
    t: number,
    material: THREE.Material,
    parent: THREE.Object3D = room,
  ) {
    const mesh = new THREE.Mesh(boxGeometry(w, h, z), material);
    mesh.position.set(x, y, t);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    parent.add(mesh);
    return mesh;
  }
  function texture(
    draw: (ctx: CanvasRenderingContext2D, w: number, h: number) => void,
    w = 256,
    h = 512,
  ) {
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    draw(c.getContext('2d')!, w, h);
    const t = new THREE.CanvasTexture(c);
    t.colorSpace = THREE.SRGBColorSpace;
    t.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
    return t;
  }

  const grilleTexture = texture(
    (ctx, w, h) => {
      ctx.fillStyle = '#6d7882';
      ctx.fillRect(0, 0, w, h);
      for (let y = 12; y < h - 12; y += 8) {
        ctx.fillStyle = '#303e49';
        ctx.fillRect(15, y, w - 30, 3);
        ctx.fillStyle = '#a4acb3';
        ctx.fillRect(15, y + 3, w - 30, 1);
      }
    },
    128,
    256,
  );
  const grilleMat = new THREE.MeshStandardMaterial({
    map: grilleTexture,
    roughness: 0.62,
    metalness: 0.5,
  });
  const roofMat = new THREE.MeshStandardMaterial({
    color: 0x608097,
    roughness: 0.5,
    metalness: 0.3,
  });
  const rackShell = new THREE.MeshStandardMaterial({
    color: 0x263f57,
    roughness: 0.43,
    metalness: 0.27,
  });
  const rackFrame = new THREE.MeshStandardMaterial({
    color: 0x5b788e,
    roughness: 0.34,
    metalness: 0.28,
  });
  const rackLine = new THREE.LineBasicMaterial({
    color: 0x4799c7,
    transparent: true,
    opacity: 0.36,
  });
  const rackEdges = new THREE.EdgesGeometry(boxGeometry(1.31, 3.08, 1.18));
  const doorLine = new THREE.MeshBasicMaterial({
    color: 0x74b3d7,
    transparent: true,
    opacity: 0.54,
  });
  const floorTexture = texture(
    (ctx, w, h) => {
      ctx.fillStyle = '#7d8a96';
      ctx.fillRect(0, 0, w, h);
      ctx.strokeStyle = '#516270';
      ctx.lineWidth = 2;
      ctx.strokeRect(1, 1, w - 2, h - 2);
      ctx.strokeStyle = '#a0aeb9';
      ctx.lineWidth = 1;
      ctx.strokeRect(4, 4, w - 8, h - 8);
      ctx.fillStyle = '#68757e';
      [
        [8, 8],
        [w - 8, 8],
        [8, h - 8],
        [w - 8, h - 8],
      ].forEach(([x, y]) => ctx.fillRect(x, y, 2, 2));
    },
    128,
    128,
  );
  floorTexture.wrapS = floorTexture.wrapT = THREE.RepeatWrapping;
  floorTexture.repeat.set(18, 14);
  const floorMat = new THREE.MeshStandardMaterial({
    map: floorTexture,
    roughness: 0.62,
    metalness: 0.12,
  });
  box(d.room.width, 0.3, d.room.depth, 0, -0.15, 0, trim);
  box(d.room.width, 0.08, d.room.depth, 0, 0.04, 0, floorMat);
  // 侧墙降低高度，移除屋顶；后墙保留空间边界。
  box(18, 0.95, 0.18, 0, 0.58, -7, wall);
  box(0.18, 0.72, 14, -9, 0.45, 0, wall);
  box(0.18, 0.72, 14, 9, 0.45, 0, wall);
  box(18, 0.06, 0.06, 0, 1.08, -6.95, dimBlue);
  box(0.055, 0.055, 14, -8.94, 0.86, 0, dimBlue);
  box(0.055, 0.055, 14, 8.94, 0.86, 0, dimBlue);
  // 空调与配电柜：中性银灰材质、格栅及小型状态显示。
  const hitTargets: THREE.Object3D[] = [],
    facilityObjects = new Map<string, THREE.Object3D>();
  for (const facility of airConditioners) {
    const [x, y, z] = facility.position;
    const group = new THREE.Group();
    group.userData.facilityId = facility.id;
    room.add(group);
    facilityObjects.set(facility.id, group);
    hitTargets.push(group);
    box(0.85, 2.7, 1.4, x, y, z, silver, group);
    const front = new THREE.Mesh(new THREE.PlaneGeometry(0.69, 1.95), grilleMat);
    front.position.set(x, 1.3, z + 0.705);
    group.add(front);
    box(0.27, 0.18, 0.035, x, 2.54, z + 0.73, trim, group);
    box(0.18, 0.05, 0.04, x, 2.54, z + 0.75, dimBlue, group);
    box(0.03, 0.18, 0.045, x + 0.3, 1.4, z + 0.73, trim, group);
  }
  const distributionLabel = texture(
    (ctx, w, h) => {
      ctx.fillStyle = '#16394e';
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = '#ffe58c';
      ctx.font = 'bold 40px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('配电柜', w / 2, h / 2);
    },
    256,
    80,
  );
  [-4.5, 0, 4.5].forEach((x) => {
    const label = new THREE.Mesh(
      new THREE.PlaneGeometry(1.35, 0.42),
      new THREE.MeshBasicMaterial({ map: distributionLabel }),
    );
    label.position.set(x, 1.8, -6.085);
    room.add(label);
    box(1.7, 2.65, 0.7, x, 1.4, -6.45, silver);
    box(1.54, 0.025, 0.03, x, 1.33, -6.08, trim);
    box(0.32, 0.25, 0.04, x, 2.23, -6.07, dark);
    box(0.24, 0.06, 0.05, x, 2.23, -6.04, dimBlue);
  });

  const meshMat = new THREE.MeshPhysicalMaterial({
    color: 0x92c6e5,
    transparent: true,
    opacity: 0.13,
    roughness: 0.1,
    metalness: 0.05,
    side: THREE.DoubleSide,
    depthWrite: false,
  });
  // Four hardware families use distinct front panels; geometry remains independently selectable/openable.
  function moduleTexture(kind: string) {
    return texture(
      (ctx, w, h) => {
        const shades = (
          {
            control: ['#86afb4', '#3a6772', '#173d47'],
            ports: ['#b4bab6', '#858f93', '#4c5d69'],
            server: ['#7c91a7', '#384e69', '#172a42'],
            fans: ['#647f93', '#273d51', '#132633'],
          } as Record<string, [string, string, string]>
        )[kind]!;
        const metal = ctx.createLinearGradient(0, 0, 0, h);
        metal.addColorStop(0, shades[0]);
        metal.addColorStop(0.16, shades[1]);
        metal.addColorStop(1, shades[2]);
        ctx.fillStyle = metal;
        ctx.fillRect(0, 0, w, h);
        ctx.strokeStyle = '#9bb7ca';
        ctx.lineWidth = 2;
        ctx.strokeRect(2, 2, w - 4, h - 4);
        if (kind === 'control') {
          ctx.fillStyle = '#274153';
          ctx.fillRect(20, 10, w - 40, h - 20);
          for (let j = 0; j < 2; j++)
            for (let i = 0; i < 16; i++) {
              ctx.fillStyle = '#071826';
              ctx.fillRect(65 + i * 25, 20 + j * 28, 12, 13);
              ctx.fillStyle = '#5b859f';
              ctx.fillRect(66 + i * 25, 20 + j * 28, 10, 2);
            }
          ['#52f4d0', '#ffd564'].forEach((c, i) => {
            ctx.fillStyle = c;
            ctx.shadowBlur = 9;
            ctx.shadowColor = c;
            ctx.fillRect(28, 20 + i * 27, 16, 16);
            ctx.shadowBlur = 0;
          });
        } else if (kind === 'ports') {
          for (let i = 0; i < 10; i++) {
            const x = 20 + i * 47;
            ctx.fillStyle = '#95a9b7';
            ctx.fillRect(x, 9, 41, 77);
            ctx.strokeStyle = '#203747';
            ctx.strokeRect(x, 9, 41, 77);
            ctx.fillStyle = '#142732';
            ctx.fillRect(x + 9, 16, 22, 26);
            ctx.fillStyle = '#344854';
            ctx.beginPath();
            ctx.arc(x + 20, 62, 8, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = ['#5cdbca', '#7bc8ff', '#edba6a'][i % 3];
            ctx.beginPath();
            ctx.arc(x + 20, 62, 3, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = '#b9c9d0';
            ctx.fillRect(x + 6, 77, 28, 3);
          }
        } else if (kind === 'server') {
          ctx.fillStyle = '#091b2b';
          ctx.fillRect(23, 17, 300, 56);
          for (let j = 0; j < 4; j++)
            for (let i = 0; i < 28; i++) {
              ctx.fillStyle = '#53718b';
              ctx.fillRect(28 + i * 10, 22 + j * 12, 4, 4);
            }
          ctx.fillStyle = '#102735';
          ctx.fillRect(344, 20, 113, 49);
          for (let i = 0; i < 4; i++) {
            ctx.fillStyle = '#607f90';
            ctx.fillRect(352 + i * 25, 28, 18, 16);
            ctx.fillStyle = '#3fe6c8';
            ctx.fillRect(353 + i * 25, 51, 4, 3);
          }
          ctx.fillStyle = '#e3c589';
          ctx.beginPath();
          ctx.arc(479, 43, 5, 0, Math.PI * 2);
          ctx.fill();
        } else if (kind === 'fans') {
          ctx.fillStyle = '#0b1b2a';
          ctx.fillRect(20, 10, w - 40, h - 20);
          for (let i = 0; i < 5; i++) {
            const x = 67 + i * 94;
            ctx.strokeStyle = '#49677d';
            ctx.lineWidth = 2;
            for (let r = 9; r < 34; r += 6) {
              ctx.beginPath();
              ctx.arc(x, 48, r, 0, Math.PI * 2);
              ctx.stroke();
            }
            for (let k = 0; k < 8; k++) {
              const a = (k * Math.PI) / 4;
              ctx.beginPath();
              ctx.moveTo(x + Math.cos(a) * 9, 48 + Math.sin(a) * 9);
              ctx.lineTo(x + Math.cos(a + 0.4) * 32, 48 + Math.sin(a + 0.4) * 32);
              ctx.stroke();
            }
            ctx.fillStyle = '#5a7890';
            ctx.beginPath();
            ctx.arc(x, 48, 7, 0, Math.PI * 2);
            ctx.fill();
          }
        }
        [
          [10, 10],
          [w - 10, 10],
          [10, h - 10],
          [w - 10, h - 10],
        ].forEach(([x, y]) => {
          ctx.fillStyle = '#c6d5dd';
          ctx.beginPath();
          ctx.arc(x, y, 2, 0, Math.PI * 2);
          ctx.fill();
        });
      },
      512,
      96,
    );
  }
  const hardwareMats: Record<string, THREE.MeshStandardMaterial> = Object.fromEntries(
    ['control', 'ports', 'server', 'fans'].map((kind) => [
      kind,
      new THREE.MeshStandardMaterial({ map: moduleTexture(kind), roughness: 0.48, metalness: 0.4 }),
    ]),
  );
  const bladeTexture = texture(
    (ctx, w, h) => {
      const gradient = ctx.createLinearGradient(0, 0, w, 0);
      gradient.addColorStop(0, '#203e47');
      gradient.addColorStop(0.18, '#8eb6be');
      gradient.addColorStop(0.25, '#436c76');
      gradient.addColorStop(1, '#1e3948');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = '#102b36';
      ctx.fillRect(13, 13, 38, h - 26);
      for (let y = 24; y < h - 30; y += 19) {
        ctx.fillStyle = '#182630';
        ctx.fillRect(17, y, 28, 10);
        ctx.fillStyle = '#a6b7b5';
        ctx.fillRect(17, y, 28, 2);
      }
      ctx.fillStyle = '#e7bd76';
      ctx.fillRect(9, h - 21, 46, 4);
      ctx.fillStyle = '#53e7b9';
      ctx.fillRect(23, 14, 10, 5);
    },
    64,
    256,
  );
  const bladeMat = new THREE.MeshStandardMaterial({
    map: bladeTexture,
    roughness: 0.4,
    metalness: 0.22,
  });
  const handleAlloy = new THREE.MeshStandardMaterial({
    color: 0xcfa96e,
    roughness: 0.4,
    metalness: 0.42,
  });
  const slotMat = new THREE.MeshStandardMaterial({
    color: 0x142c41,
    roughness: 0.54,
    metalness: 0.35,
  });
  const softCyan = new THREE.MeshBasicMaterial({ color: 0x54cae8 });
  const softMint = new THREE.MeshBasicMaterial({ color: 0x6beac3 });
  const sideVent = texture(
    (ctx, w, h) => {
      ctx.fillStyle = '#102230';
      ctx.fillRect(0, 0, w, h);
      for (let y = 5; y < h; y += 8) {
        ctx.fillStyle = '#2b4357';
        ctx.fillRect(4, y, w - 8, 2);
      }
    },
    64,
    256,
  );
  const sideVentMat = new THREE.MeshStandardMaterial({
    map: sideVent,
    roughness: 0.6,
    metalness: 0.3,
  });
  const reflectionTexture = texture(
    (ctx, w, h) => {
      const gradient = ctx.createLinearGradient(0, 0, w, h * 0.7);
      gradient.addColorStop(0, '#ffffff00');
      gradient.addColorStop(0.24, '#ffffff02');
      gradient.addColorStop(0.38, '#d8f6ff38');
      gradient.addColorStop(0.43, '#ffffff10');
      gradient.addColorStop(0.62, '#ffffff00');
      gradient.addColorStop(0.85, '#ffffff04');
      gradient.addColorStop(1, '#d8f6ff22');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, w, h);
    },
    256,
    512,
  );
  const reflectionMat = new THREE.MeshBasicMaterial({
    map: reflectionTexture,
    transparent: true,
    opacity: 0.68,
    depthWrite: false,
    side: THREE.DoubleSide,
  });
  const racks: Rack[] = [],
    lights: THREE.Object3D[] = [];
  const planeGeo = new THREE.PlaneGeometry(1.07, 0.185),
    ledGeo = boxGeometry(0.022, 0.018, 0.024);
  const matrix = new THREE.Matrix4();
  function instances(
    geometry: THREE.BufferGeometry,
    material: THREE.Material,
    positions: number[][],
    parent: THREE.Object3D,
    shadows = false,
  ) {
    const mesh = new THREE.InstancedMesh(geometry, material, positions.length);
    positions.forEach((p, i) => mesh.setMatrixAt(i, matrix.makeTranslation(p[0]!, p[1]!, p[2]!)));
    mesh.instanceMatrix.needsUpdate = true;
    mesh.castShadow = shadows;
    mesh.receiveShadow = shadows;
    parent.add(mesh);
    return mesh;
  }
  const alarmColors = Object.fromEntries(
    alarmLevels.map((level) => [level.key, new THREE.Color(level.color).getHex()]),
  );
  // A fixed, cabinet-shaped edge glow: no detached expanding circles or colored volume.
  const alarmEdgeTexture = texture(
    (ctx, w, h) => {
      const pixels = ctx.createImageData(w, h);
      for (let y = 0; y < h; y++)
        for (let x = 0; x < w; x++) {
          const px = Math.abs(((x + 0.5) / w) * 2 - 1),
            py = Math.abs(((y + 0.5) / h) * 2 - 1);
          const qx = px - 0.8,
            qy = py - 0.8;
          const distance =
            Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - 0.045;
          const alpha = Math.exp(-Math.pow(distance / 0.053, 2)) * 0.5;
          const offset = (y * w + x) * 4;
          pixels.data[offset] = 255;
          pixels.data[offset + 1] = 255;
          pixels.data[offset + 2] = 255;
          pixels.data[offset + 3] = Math.round(alpha * 255);
        }
      ctx.putImageData(pixels, 0, 0);
    },
    128,
    128,
  );
  const alarmEdgeGeometry = new THREE.PlaneGeometry(1.56, 1.51);
  for (const device of d.devices) {
    const [x, , z] = device.position,
      zone = device.zone;
    const group = new THREE.Group();
    group.position.set(x, 0, z);
    group.userData.deviceId = device.id;
    room.add(group);
    hitTargets.push(group);
    // 空心柜体与十二台独立服务器，开门后真实露出层板、导轨及设备面板。
    box(0.065, 3.0, 1.16, -0.615, 1.61, 0, rackShell, group);
    box(0.065, 3.0, 1.16, 0.615, 1.61, 0, rackShell, group);
    box(1.2, 3.0, 0.06, 0, 1.61, -0.55, rackShell, group);
    box(1.3, 0.09, 1.16, 0, 3.16, 0, rackFrame, group);
    box(1.3, 0.11, 1.16, 0, 0.13, 0, rackFrame, group);
    box(0.035, 2.86, 0.03, -0.555, 1.62, 0.43, silver, group);
    box(0.035, 2.86, 0.03, 0.555, 1.62, 0.43, silver, group);
    const roof = new THREE.Mesh(new THREE.PlaneGeometry(1.03, 0.85), roofMat);
    roof.rotation.x = -Math.PI / 2;
    roof.position.set(0, 3.209, 0);
    group.add(roof);
    const topVents = [];
    for (let v = 0; v < 3; v++) topVents.push([0, 3.213, -0.23 + v * 0.23]);
    instances(boxGeometry(0.7, 0.004, 0.018), roofMat, topVents, group);
    const cabinetOutline = new THREE.LineSegments(rackEdges, rackLine);
    cabinetOutline.position.y = 1.65;
    group.add(cabinetOutline);
    const decorativeHardware = new THREE.Group();
    decorativeHardware.name = 'decorativeHardware';
    group.add(decorativeHardware);
    decorativeHardware.visible = !options.data?.platform;
    const unitPositions = [],
      handlePositions: number[][] = [],
      ledPositions = [];
    const families: Record<string, number[][]> = { control: [], ports: [], server: [], fans: [] };
    for (let slot = 0; slot < 12; slot++) {
      const y = 0.36 + slot * 0.226;
      unitPositions.push([0, y, -0.02]);
      [-0.51, 0.51].forEach((side) => handlePositions.push([side, y, 0.46]));
      if (slot === 11) families.control.push([0, y, 0.436]);
      else if (slot === 10) families.ports.push([0, y, 0.436]);
      else if (slot === 0 || slot === 1) families.fans.push([0, y, 0.436]);
      else if (slot === 2 || slot === 3 || slot === 6) families.server.push([0, y, 0.436]);
      ledPositions.push([0.495, y, 0.461]);
    }
    instances(boxGeometry(1.07, 0.195, 0.9), slotMat, unitPositions, decorativeHardware, true);
    for (const [kind, positions] of Object.entries(families))
      if (positions.length) instances(planeGeo, hardwareMats[kind], positions, decorativeHardware);
    instances(boxGeometry(0.025, 0.09, 0.026), silver, handlePositions, decorativeHardware);
    instances(ledGeo, softMint, ledPositions, decorativeHardware).name = 'runningIndicators';
    // Upper and lower blade bays have actual depth, narrow removable cards and release handles.
    const cardPositions = [],
      cardHandles = [],
      cardLights = [];
    for (const y of [1.37, 2.29])
      for (let c = 0; c < 10; c++) {
        const cx = -0.46 + c * 0.102;
        cardPositions.push([cx, y, 0.455]);
        cardHandles.push([cx + 0.022, y, 0.485]);
        cardLights.push([cx, y + 0.105, 0.478]);
      }
    const cards = instances(
      boxGeometry(0.073, 0.39, 0.11),
      bladeMat,
      cardPositions,
      decorativeHardware,
    );
    cardPositions.forEach((_, i) =>
      cards.setColorAt(
        i,
        new THREE.Color([0xc4dde0, 0xa3c9ba, 0xa4b9d9, 0xd2c6ac, 0x91b9c9][i % 5]),
      ),
    );
    instances(boxGeometry(0.013, 0.25, 0.012), handleAlloy, cardHandles, decorativeHardware);
    instances(boxGeometry(0.013, 0.022, 0.012), softCyan, cardLights, decorativeHardware).name =
      'runningIndicators';
    // Side ventilation is visible in the angled inspection camera.
    for (const side of [-1, 1]) {
      const vent = new THREE.Mesh(new THREE.PlaneGeometry(0.69, 2.36), sideVentMat);
      vent.rotation.y = (side * Math.PI) / 2;
      vent.position.set(side * 0.65, 1.63, 0);
      group.add(vent);
    }
    const rackScrews: number[][] = [];
    for (let j = 0; j < 35; j++)
      [-0.574, 0.574].forEach((cx) => rackScrews.push([cx, 0.25 + j * 0.079, 0.596]));
    instances(boxGeometry(0.012, 0.012, 0.008), silver, rackScrews, group);
    const door = new THREE.Group();
    door.name = 'doorPivot';
    door.userData.door = true;
    door.position.set(-0.642, 0, 0.625);
    group.add(door);
    box(0.048, 2.98, 0.055, 0.022, 1.63, 0, rackFrame, door);
    box(0.048, 2.98, 0.055, 1.262, 1.63, 0, rackFrame, door);
    box(1.28, 0.065, 0.055, 0.642, 0.17, 0, rackFrame, door);
    box(1.28, 0.065, 0.055, 0.642, 3.09, 0, rackFrame, door);
    box(0.014, 2.95, 0.012, 0.022, 1.63, 0.034, doorLine, door);
    box(0.014, 2.95, 0.012, 1.262, 1.63, 0.034, doorLine, door);
    box(1.25, 0.013, 0.012, 0.642, 0.17, 0.034, doorLine, door);
    box(1.25, 0.013, 0.012, 0.642, 3.09, 0.034, doorLine, door);
    const doorMesh = new THREE.Mesh(new THREE.PlaneGeometry(1.18, 2.86), meshMat);
    doorMesh.position.set(0.642, 1.63, 0);
    door.add(doorMesh);
    const reflection = new THREE.Mesh(new THREE.PlaneGeometry(1.17, 2.84), reflectionMat);
    reflection.position.set(0.642, 1.63, 0.005);
    door.add(reflection);
    box(0.04, 0.36, 0.06, 1.17, 1.64, 0.042, silver, door);
    box(0.018, 0.12, 0.063, 1.17, 1.67, 0.045, dimBlue, door);
    [0.52, 2.7].forEach((y) => box(0.085, 0.15, 0.09, 0, y, 0, silver, door));
    const stateMat = new THREE.MeshBasicMaterial({
      color: device.alarm ? alarmColors[device.alarm.severity] : 0x39c7ff,
    });
    const beacon = box(0.14, 0.037, 0.035, 0.46, 3.13, 0.61, stateMat, group);
    box(0.062, 0.37, 0.13, 0.44, 2.29, 0.465, stateMat, group);
    const strip = box(0.012, 2.78, 0.018, -0.57, 1.61, 0.465, stateMat, group);
    const highlight = new THREE.LineSegments(
      new THREE.EdgesGeometry(boxGeometry(1.33, 3.18, 1.3)),
      new THREE.LineBasicMaterial({
        color: device.alarm ? alarmColors[device.alarm.severity] : 0x55dfff,
        transparent: true,
        opacity: 0.72,
      }),
    );
    highlight.name = 'selectionOutline';
    highlight.position.y = 1.63;
    highlight.visible = false;
    group.add(highlight);
    // Alarm visibility belongs to device state, independently of hover and patrol selection.
    const alarmVisual = new THREE.Group();
    alarmVisual.name = 'activeAlarm';
    alarmVisual.visible = Boolean(device.alarm);
    group.add(alarmVisual);
    const alarmColor = device.alarm ? alarmColors[device.alarm.severity] : alarmColors.critical;
    const alarmOutline = new THREE.LineSegments(
      new THREE.EdgesGeometry(boxGeometry(1.32, 3.13, 1.28)),
      new THREE.LineBasicMaterial({
        color: alarmColor,
        transparent: true,
        opacity: 0.42,
        depthWrite: false,
      }),
    );
    alarmOutline.position.y = 1.66;
    alarmVisual.add(alarmOutline);
    const alarmEdge = new THREE.Mesh(
      alarmEdgeGeometry,
      new THREE.MeshBasicMaterial({
        map: alarmEdgeTexture,
        color: alarmColor,
        transparent: true,
        opacity: 0.7,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide,
      }),
    );
    alarmEdge.name = 'alarmEdge';
    alarmEdge.raycast = () => {};
    alarmEdge.rotation.x = -Math.PI / 2;
    alarmEdge.position.y = 3.225;
    alarmVisual.add(alarmEdge);
    const alarmFill = new THREE.Mesh(
      boxGeometry(1.315, 3.13, 1.275),
      new THREE.MeshBasicMaterial({
        color: alarmColor,
        transparent: true,
        opacity: 0.14,
        depthWrite: false,
      }),
    );
    alarmFill.position.y = 1.66;
    alarmFill.raycast = () => {};
    alarmFill.name = 'alarmFill';
    alarmVisual.add(alarmFill);
    racks.push({
      id: device.id,
      group,
      roof,
      highlight,
      alarmVisual,
      alarmEdge,
      alarmOutline,
      alarmFill,
      zone,
      door,
      doorOpen: false,
      doorAngle: 0,
      stateMat,
      beacon,
      strip,
      alarm: device.alarm,
    });
    // 上方线缆桥架沿纵深排列。
    // 省略顶部通长桥架，保持整间机房轮廓安静清晰。
  }
  // 后侧暗黄色护栏，细节保持低亮度。
  [-8.2, 8.2].forEach((x) => {
    [-5.8, -3.8].forEach((z) => box(0.045, 0.6, 0.045, x, 1.16, z, gold));
    box(0.045, 0.045, 2, x, 1.46, -4.8, gold);
  });
  // 玻璃立面：透明玻璃、可辨认的金属立柱及中央双开门。
  const glass = new THREE.MeshPhysicalMaterial({
    color: 0xbad7e8,
    transparent: true,
    opacity: 0.12,
    roughness: 0.09,
    metalness: 0.05,
    side: THREE.DoubleSide,
    depthWrite: false,
  });
  [-9, 9].forEach((x) => {
    [-7, 0, 7].forEach((z) => box(0.065, 3.65, 0.065, x, 1.9, z, trim));
    box(0.085, 0.085, 14, x, 3.72, 0, trim);
    box(0.035, 0.026, 14, x, 3.77, 0, blue);
    box(0.025, 2.8, 14, x, 2.1, 0, glass).castShadow = false;
  });
  box(18, 0.07, 0.07, 0, 3.72, -7, trim);
  box(18, 0.026, 0.03, 0, 3.77, -7, dimBlue);
  for (let i = 0; i < 8; i++) {
    const x = -7.875 + i * 2.25;
    box(2.2, 3.5, 0.035, x, 1.84, 7, glass).castShadow = false;
  }
  for (let i = 0; i < 9; i++) box(0.065, 3.65, 0.09, -9 + i * 2.25, 1.9, 7, trim);
  box(18, 0.085, 0.1, 0, 3.72, 7, trim);
  box(18, 0.1, 0.14, 0, 0.13, 7, trim);
  box(18, 0.035, 0.035, 0, 3.78, 7, blue);
  [-0.9, 0.9].forEach((x) => {
    box(0.055, 3.28, 0.09, x, 1.74, 7.05, silver);
    box(0.045, 0.54, 0.08, x < 0 ? -0.12 : 0.12, 1.55, 7.09, silver);
  });
  box(1.8, 0.07, 0.09, 0, 3.4, 7.05, silver);
  // 摄像头：固定支架、白色机身、遮阳罩与朝向机房内部的镜头。
  const cameraShell = mat(0xd8e5ee, 0.35, 0.35);
  const cameraLens = new THREE.MeshPhysicalMaterial({
    color: 0x12384f,
    metalness: 0.55,
    roughness: 0.08,
  });
  const lensGeometry = new THREE.CylinderGeometry(0.14, 0.14, 0.065, 24);
  const lensGlassGeometry = new THREE.CircleGeometry(0.105, 24);
  let cameraNumber = 0;
  for (const roomCamera of cameras) {
    const mount = new THREE.Group();
    mount.name = `cornerCamera-${++cameraNumber}`;
    mount.userData.cameraId = roomCamera.id;
    mount.userData.facilityId = roomCamera.id;
    facilityObjects.set(roomCamera.id, mount);
    hitTargets.push(mount);
    mount.position.set(...roomCamera.position);
    mount.lookAt(0, 3.42, 0);
    room.add(mount);
    box(0.28, 0.36, 0.09, 0, 0, 0, silver, mount);
    box(0.1, 0.1, 0.55, 0, -0.07, 0.29, trim, mount);
    box(0.12, 0.24, 0.12, 0, 0.02, 0.52, silver, mount);
    const head = new THREE.Group();
    head.position.set(0, 0.12, 0.56);
    head.rotation.x = 0.2;
    mount.add(head);
    box(0.48, 0.3, 0.68, 0, 0, 0.18, cameraShell, head);
    box(0.56, 0.055, 0.82, 0, 0.18, 0.22, cameraShell, head);
    box(0.39, 0.23, 0.045, 0, 0, 0.535, black, head);
    const lens = new THREE.Mesh(lensGeometry, trim);
    lens.rotation.x = Math.PI / 2;
    lens.position.set(0, 0, 0.58);
    head.add(lens);
    const lensGlass = new THREE.Mesh(lensGlassGeometry, cameraLens);
    lensGlass.position.set(0, 0, 0.616);
    head.add(lensGlass);
    box(0.04, 0.035, 0.018, 0.15, -0.075, 0.565, softMint, head);
  }
  // 同一套有色透明平台用于机房总览与单台检视；齿圈在水平面缓慢旋转。
  const platformTexture = texture(
    (ctx, w, h) => {
      const g = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
      g.addColorStop(0, '#031425');
      g.addColorStop(0.58, '#06253b');
      g.addColorStop(0.86, '#07324c');
      g.addColorStop(1, '#126081');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
    },
    512,
    512,
  );
  const rotors: { group: THREE.Group; speed: number }[] = [];
  function techBase(radius: number, parent: THREE.Object3D, flatten = 1, filled = true) {
    const root = new THREE.Group();
    root.scale.z = flatten;
    parent.add(root);
    const plate = new THREE.Mesh(
      new THREE.CylinderGeometry(radius * 0.95, radius * 0.97, radius * 0.009, 96),
      new THREE.MeshPhysicalMaterial({
        color: 0x053151,
        emissive: 0x032c46,
        emissiveIntensity: 0.2,
        transparent: true,
        opacity: 0.36,
        roughness: 0.24,
        metalness: 0.4,
        depthWrite: false,
      }),
    );
    plate.position.y = -radius * 0.032;
    plate.visible = filled;
    root.add(plate);
    const top = new THREE.Mesh(
      new THREE.CircleGeometry(radius * 0.95, 96),
      new THREE.MeshBasicMaterial({
        map: platformTexture,
        transparent: true,
        opacity: 0.15,
        depthWrite: false,
        side: THREE.DoubleSide,
      }),
    );
    top.rotation.x = -Math.PI / 2;
    top.position.y = -radius * 0.018;
    top.visible = filled;
    root.add(top);
    const gear = new THREE.Group();
    root.add(gear);
    rotors.push({ group: gear, speed: 0.045 });
    const tickMaterial = new THREE.MeshBasicMaterial({
      color: 0x559cbc,
      transparent: true,
      opacity: 0.58,
      side: THREE.DoubleSide,
    });
    for (let i = 0; i < 48; i++) {
      const tick = new THREE.Mesh(
        new THREE.RingGeometry(
          radius * 0.969,
          radius * (i % 4 === 0 ? 1.016 : 1.0),
          2,
          1,
          (i * Math.PI) / 24,
          0.023,
        ),
        tickMaterial,
      );
      tick.rotation.x = -Math.PI / 2;
      gear.add(tick);
    }
    const inner = new THREE.Group();
    inner.position.y = 0.008;
    root.add(inner);
    rotors.push({ group: inner, speed: -0.035 });
    for (let i = 0; i < 4; i++) {
      const segment = new THREE.Mesh(
        new THREE.RingGeometry(radius * 0.871, radius * 0.883, 32, 1, (i * Math.PI) / 2, 0.98),
        new THREE.MeshBasicMaterial({
          color: i % 2 ? 0x27729d : 0x70aec5,
          transparent: true,
          opacity: 0.66,
          side: THREE.DoubleSide,
        }),
      );
      segment.rotation.x = -Math.PI / 2;
      inner.add(segment);
    }
    return root;
  }
  const base = new THREE.Group();
  base.position.set(0, -1.08, 4.4);
  scene.add(base);
  techBase(7.0, base, 0.48);
  const labelLayer = local('zoneLabels'),
    svgNS = 'http://www.w3.org/2000/svg';
  const zoneLines = document.createElementNS(svgNS, 'svg');
  zoneLines.classList.add('zone-connectors');
  zoneLines.setAttribute('aria-hidden', 'true');
  labelLayer.appendChild(zoneLines);
  const labels = d.zones.map((zone) => {
    const el = document.createElement('div');
    el.className = 'zone-label';
    el.dataset.zone = zone.id;
    el.innerHTML = `<span>${icon('pin')}${zone.id}区</span><strong>${zone.temperature}<small>℃</small></strong>`;
    labelLayer.appendChild(el);
    const line = document.createElementNS(svgNS, 'line');
    zoneLines.appendChild(line);
    return { zone, el, line, point: new THREE.Vector3(...zone.position) };
  });

  const inspectionRoot = new THREE.Group();
  inspectionRoot.visible = false;
  scene.add(inspectionRoot);
  const inspectionLight = new THREE.DirectionalLight(0xe4f4ff, 2.3);
  inspectionLight.position.set(-3, 4.5, 6);
  inspectionRoot.add(inspectionLight);
  const inspectionPlatform = techBase(1.6, inspectionRoot, 1, false);
  inspectionPlatform.position.y = 0.04;
  const rackMap = new Map(racks.map((r) => [r.id, r]));
  let hovered: string | null = null,
    selected: string | null = null,
    expanded = false,
    inspected: Rack | null = null,
    inspectionClone: THREE.Group | null = null,
    cloneDoor: THREE.Object3D | null = null,
    selectedDeviceId: string | null = null;
  const targetRoofMat = new THREE.MeshStandardMaterial({
    color: 0xffcb67,
    emissive: 0xe89c25,
    emissiveIntensity: 0.45,
    roughness: 0.4,
    metalness: 0.25,
  });
  const targetGlow = new THREE.Mesh(
    boxGeometry(1.4, 3.24, 1.33),
    new THREE.MeshBasicMaterial({
      color: 0xffc75b,
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
      depthTest: false,
    }),
  );
  targetGlow.visible = false;
  targetGlow.renderOrder = 50;
  room.add(targetGlow);
  let markedId: string | null = null;
  const targetPin = document.createElement('div');
  targetPin.id = 'patrolTargetPin';
  targetPin.className = 'patrol-target-pin';
  targetPin.hidden = true;
  targetPin.setAttribute('role', 'status');
  targetPin.setAttribute('aria-live', 'polite');
  targetPin.innerHTML = `<span>${icon('pin')}巡检目标</span><strong></strong><i aria-hidden="true"></i>`;
  viewport.appendChild(targetPin);
  const alarmPin = document.createElement('button');
  alarmPin.className = 'active-alarm-pin';
  alarmPin.hidden = true;
  alarmPin.innerHTML =
    '<span><i></i> 严重告警</span><strong></strong><small></small><em>让小维检查 →</em>';
  viewport.appendChild(alarmPin);
  listen(alarmPin, 'click', () => {
    if (alarmPin.dataset.device) options.onAlarmInspect?.(alarmPin.dataset.device);
  });
  function markTarget(id: string | null) {
    racks.forEach((r) => {
      r.roof.material = roofMat;
      r.highlight.material.color.set(r.alarm ? alarmColors[r.alarm.severity] : 0x55dfff);
      r.highlight.material.depthTest = true;
      r.highlight.material.opacity = 0.72;
      r.highlight.renderOrder = 0;
    });
    markedId = id;
    host.dataset.patrolHighlight = id || '';
    const rack = rackMap.get(id ?? '');
    targetGlow.visible = Boolean(rack);
    targetPin.hidden = !rack;
    targetPin.querySelector('strong')!.textContent = rack?.id || '';
    labels.forEach((l) => l.el.classList.remove('near-patrol-target'));
    if (rack) {
      if (!rack.alarm) rack.roof.material = targetRoofMat;
      targetGlow.material.color.set(rack.alarm ? alarmColors[rack.alarm.severity] : 0xffc75b);
      targetPin.classList.toggle('alarm-target', Boolean(rack.alarm));
      targetPin.querySelector('span')!.innerHTML =
        icon('pin') + (rack.alarm ? '告警设备 · 巡检中' : '巡检目标');
      targetGlow.position.set(rack.group.position.x, 1.64, rack.group.position.z);
      rack.highlight.material.color.set(rack.alarm ? alarmColors[rack.alarm.severity] : 0xffd778);
      rack.highlight.material.depthTest = false;
      rack.highlight.renderOrder = 51;
    }
    highlight(null, id);
  }
  const raycaster = new THREE.Raycaster(),
    pointer = new THREE.Vector2(),
    hoverCard = local('deviceHover');
  function highlight(zone: string | null, id: string | null = null) {
    racks.forEach((r) => (r.highlight.visible = id ? r.id === id : r.zone === zone));
    labels.forEach((l) => {
      l.el.classList.toggle('is-active', l.zone.id === zone);
    });
    viewport.dataset.highlight = zone || rackMap.get(id ?? '')?.zone || '';
    viewport.dataset.highlightDevice = id || '';
  }
  function openZone(zone: SceneInput['zones'][number]) {
    selected = zone.id;
    highlight(selected);
    const list = racks.filter((r) => r.zone === zone.id),
      alarms = list.filter((r) => r.alarm).length;
    const detail = local('zoneDetail');
    detail.querySelector('h3')!.textContent = `${zone.id}区 · 区域信息`;
    detail.querySelector('dl')!.innerHTML =
      `<dt>平均温度</dt><dd>${zone.temperature === '未采集' ? '未采集' : zone.temperature + ' ℃'}</dd><dt>平均湿度</dt><dd>${zone.humidity === '未采集' ? '未采集' : zone.humidity + ' %'}</dd><dt>展示机柜</dt><dd>${list.length} 台</dd><dt>当前告警</dt><dd>${alarms} 台</dd>`;
    detail.hidden = false;
    local('closeDetail').focus();
  }
  function closeDetail() {
    local('zoneDetail').hidden = true;
    selected = null;
    highlight(null, hovered || selectedDeviceId);
  }
  listen(local('closeDetail'), 'click', () => closeDetail());
  function hitTest(e: PointerEvent) {
    const b = renderer.domElement.getBoundingClientRect();
    pointer.set(
      ((e.clientX - b.left) / b.width) * 2 - 1,
      (-(e.clientY - b.top) / b.height) * 2 + 1,
    );
    raycaster.setFromCamera(pointer, camera);
    return raycaster
      .intersectObjects(inspected && inspectionClone ? [inspectionClone] : hitTargets, true)
      .find((hit) => hit.object instanceof THREE.Mesh && hit.object.visible);
  }
  function cameraFromHit(hit: THREE.Intersection | undefined) {
    let o: THREE.Object3D | null | undefined = hit?.object;
    while (o && !o.userData.cameraId) o = o.parent;
    return o?.userData.cameraId;
  }
  function deviceFromHit(hit: THREE.Intersection | undefined) {
    let o: THREE.Object3D | null | undefined = hit?.object;
    while (o && !o.userData.deviceId) o = o.parent;
    return o?.userData.deviceId;
  }
  listen(renderer.domElement, 'pointermove', (e) => {
    if (inspected) {
      renderer.domElement.style.cursor = 'grab';
      return;
    }
    const hit = hitTest(e),
      cameraId = cameraFromHit(hit);
    const id = deviceFromHit(hit) || null;
    hovered = id;
    highlight(selected, selected ? null : id || selectedDeviceId);
    renderer.domElement.style.cursor = id || cameraId ? 'pointer' : expanded ? 'grab' : 'default';
    hoverCard.hidden = !id;
    if (id) {
      const device = d.devices.find((v) => v.id === id),
        rack = rackMap.get(id ?? ''),
        b = viewport.getBoundingClientRect(),
        sx = viewport.clientWidth / b.width;
      hoverCard.textContent = `${device?.name || id}（${id}） · ${device?.runtimeStatus || (rack?.alarm ? '告警' : options.data?.platform ? '未采集' : '在线')} · 点击检视`;
      hoverCard.style.left = `${THREE.MathUtils.clamp((e.clientX - b.left) * sx + 15, 5, viewport.clientWidth - 250)}px`;
      hoverCard.style.top = `${Math.min((e.clientY - b.top) * sx + 18, viewport.clientHeight - 45)}px`;
    }
  });
  listen(renderer.domElement, 'pointerleave', () => {
    hovered = null;
    hoverCard.hidden = true;
    highlight(selected, selected ? null : selectedDeviceId);
  });
  let pointerStart: { x: number; y: number; button: number } | null = null;
  listen(renderer.domElement, 'pointerdown', (e) => {
    pointerStart = { x: e.clientX, y: e.clientY, button: e.button };
  });
  listen(renderer.domElement, 'pointercancel', () => (pointerStart = null));
  listen(renderer.domElement, 'pointerup', (e) => {
    const start = pointerStart;
    pointerStart = null;
    if (!start || start.button !== 0 || Math.hypot(e.clientX - start.x, e.clientY - start.y) > 5)
      return;
    const hit = hitTest(e);
    if (inspected) {
      let o: THREE.Object3D | null | undefined = hit?.object;
      while (o && !o.userData.door) o = o.parent;
      if (o) options.onDoorClick?.(inspected.id);
    } else {
      let facilityObject: THREE.Object3D | null | undefined = hit?.object;
      while (facilityObject && !facilityObject.userData.facilityId)
        facilityObject = facilityObject.parent;
      if (facilityObject?.userData.facilityId && !facilityObject.userData.cameraId) {
        locateFacility(facilityObject.userData.facilityId);
        return;
      }
      const cameraId = cameraFromHit(hit);
      if (cameraId) {
        options.onCameraPick?.(cameraId);
        return;
      }
      const id = deviceFromHit(hit);
      if (id) options.onDevicePick?.(id);
    }
  });
  function projectLabels() {
    const w = host.clientWidth,
      h = host.clientHeight;
    const candidates = labels.map((l) => {
      const p = l.point.clone().project(camera);
      return {
        ...l,
        x: (p.x * 0.5 + 0.5) * w,
        y: (-p.y * 0.5 + 0.5) * h,
        visible: p.z > -1 && p.z < 1,
      };
    });
    // Fixed peripheral labels leave cabinet roofs available for direct picking.
    zoneLines.setAttribute('viewBox', `0 0 ${w} ${h}`);
    candidates.forEach((l) => {
      const ax = l.x,
        ay = l.y,
        top = ['A', 'B', 'C'].indexOf(l.zone.id);
      l.x = top >= 0 ? w * (0.18 + top * 0.32) : l.zone.id === 'D' ? 68 : w - 68;
      l.y = top >= 0 ? 24 : h * 0.65;
      l.el.style.left = `${l.x}px`;
      l.el.style.top = `${l.y}px`;
      l.el.hidden = !l.visible;
      l.line.setAttribute('x1', String(l.x));
      l.line.setAttribute('y1', String(l.y + 16));
      l.line.setAttribute('x2', String(ax));
      l.line.setAttribute('y2', String(ay));
      l.line.style.display = l.visible ? '' : 'none';
    });
    const priority = racks.find((r) => r.alarm?.severity === 'critical');
    alarmPin.hidden = !priority || Boolean(inspected) || priority.id === markedId;
    if (priority?.alarm && !alarmPin.hidden) {
      const p = new THREE.Vector3(
          priority.group.position.x,
          3.3,
          priority.group.position.z,
        ).project(camera),
        ax = (p.x * 0.5 + 0.5) * w,
        ay = (-p.y * 0.5 + 0.5) * h;
      const x = THREE.MathUtils.clamp(ax - 205, 12, w - 205),
        y = THREE.MathUtils.clamp(ay + 34, 110, h - 135);
      alarmPin.style.left = `${x}px`;
      alarmPin.style.top = `${y}px`;
      alarmPin.style.setProperty(
        '--alarm-line',
        `${Math.max(16, Math.hypot(ax - (x + 190), ay - y))}px`,
      );
      alarmPin.style.setProperty('--alarm-angle', `${Math.atan2(ay - y, ax - (x + 190))}rad`);
      alarmPin.dataset.device = priority.id;
      alarmPin.querySelector('strong')!.textContent = priority.id;
      alarmPin.querySelector('small')!.textContent = priority.alarm.title;
      alarmPin.setAttribute(
        'aria-label',
        `${priority.id} 严重告警：${priority.alarm.title}，让小维检查`,
      );
      alarmPin.hidden = p.z < -1 || p.z > 1;
    }
    const rack = rackMap.get(markedId ?? '');
    targetPin.hidden = !rack || Boolean(inspected);
    if (rack && !inspected) {
      const p = new THREE.Vector3(rack.group.position.x, 3.4, rack.group.position.z).project(
        camera,
      );
      const ax = (p.x * 0.5 + 0.5) * w,
        ay = (-p.y * 0.5 + 0.5) * h;
      const x = THREE.MathUtils.clamp(ax + 36, 12, w - 174),
        y = THREE.MathUtils.clamp(ay - 20, 105, h - 12);
      targetPin.style.left = `${x}px`;
      targetPin.style.top = `${y}px`;
      targetPin.style.setProperty('--anchor-x', `${ax - x}px`);
      targetPin.style.setProperty('--anchor-y', `${ay - y}px`);
      targetPin.hidden = p.z < -1 || p.z > 1;
      candidates.forEach((l) =>
        l.el.classList.toggle(
          'near-patrol-target',
          Math.abs(l.x - (x + 80)) < 135 && Math.abs(l.y - (y - 42)) < 100,
        ),
      );
    } else {
      labels.forEach((l) => l.el.classList.remove('near-patrol-target'));
    }
  }
  // 根据真实场景包围盒限制全景缩放，避免旋转到边界时裁掉玻璃立面。
  const roomCorners: THREE.Vector3[] = [];
  for (const x of [-9.08, 9.08])
    for (const y of [-0.3, 3.82])
      for (const z of [-7.08, 7.08]) roomCorners.push(new THREE.Vector3(x, y, z));
  function fitRoom() {
    if (inspected) return;
    camera.updateMatrixWorld();
    let factor = 1;
    for (const corner of roomCorners) {
      const p = corner.clone().project(camera);
      factor = Math.max(factor, Math.abs(p.x) / 0.975, Math.abs(p.y) / (expanded ? 0.89 : 0.81));
    }
    if (factor > 1.001) {
      camera.position
        .sub(controls.target)
        .multiplyScalar(Math.min(factor * 1.005, 1.2))
        .add(controls.target);
      camera.updateMatrixWorld();
    }
  }
  function resize() {
    const w = host.clientWidth,
      h = host.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    fitRoom();
    projectLabels();
  }
  const observer = new ResizeObserver(resize);
  observer.observe(host);
  resize();
  let viewTween: gsap.core.Timeline | null = null;
  function configureCamera(inspect: boolean) {
    viewTween?.kill();
    viewTween = null;
    controls.enabled = expanded;
    controls.enableDamping = false;
    controls.update();
    controls.minDistance = inspect ? 5.8 : 28;
    controls.maxDistance = inspect ? 10 : 48;
    controls.minPolarAngle = inspect ? 0.65 : 0.55;
    controls.maxPolarAngle = inspect ? 1.43 : 1;
    controls.minAzimuthAngle = inspect ? -0.85 : -0.5;
    controls.maxAzimuthAngle = inspect ? 0.85 : 0.5;
    camera.position.copy(
      inspect
        ? new THREE.Vector3(3.5, 3.9, 6.8)
        : expanded
          ? new THREE.Vector3(0, 23.4, 19.6)
          : overviewPosition,
    );
    controls.target.copy(
      inspect ? new THREE.Vector3(0, 1.6, 0) : expanded ? new THREE.Vector3(0, 0, 0) : target,
    );
    controls.update();
    controls.enableDamping = true;
    resize();
  }
  let facilityHighlight: THREE.BoxHelper | null = null;
  function clearFacility() {
    if (facilityHighlight) {
      facilityHighlight.parent?.remove(facilityHighlight);
      facilityHighlight.geometry.dispose();
      facilityHighlight.material.dispose();
      facilityHighlight = null;
    }
  }
  function locateFacility(id: string) {
    const facility = demoFacilities.find((item) => item.id === id),
      object = facilityObjects.get(id);
    if (!facility || !object) return false;
    showRoom();
    clearFacility();
    facilityHighlight = new THREE.BoxHelper(object, 0x65e4ff);
    facilityHighlight.raycast = () => {};
    room.add(facilityHighlight);
    viewTween?.kill();
    const point = new THREE.Vector3(...facility.position);
    controls.minDistance = 5;
    controls.maxDistance = 48;
    controls.minPolarAngle = 0.35;
    controls.maxPolarAngle = 1.43;
    controls.minAzimuthAngle = -Infinity;
    controls.maxAzimuthAngle = Infinity;
    controls.target.copy(point);
    camera.position.copy(
      point.clone().add(new THREE.Vector3(facility.position[0] < 0 ? 7 : -7, 6, 10)),
    );
    controls.update();
    host.dataset.facility = id;
    return true;
  }
  function resetView() {
    clearFacility();
    delete host.dataset.facility;
    const startPosition = camera.position.clone(),
      startTarget = controls.target.clone();
    configureCamera(Boolean(inspected));
    const endPosition = camera.position.clone(),
      endTarget = controls.target.clone();
    camera.position.copy(startPosition);
    controls.target.copy(startTarget);
    controls.enabled = false;
    controls.enableDamping = false;
    viewTween = gsap.timeline({
      onUpdate() {
        controls.update();
        fitRoom();
        projectLabels();
      },
      onComplete() {
        viewTween = null;
        controls.enabled = expanded;
        controls.enableDamping = true;
      },
    });
    const duration = matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 1;
    viewTween.to(
      camera.position,
      { x: endPosition.x, y: endPosition.y, z: endPosition.z, duration, ease: 'power2.inOut' },
      0,
    );
    viewTween.to(
      controls.target,
      { x: endTarget.x, y: endTarget.y, z: endTarget.z, duration, ease: 'power2.inOut' },
      0,
    );
  }
  let extractionTween: gsap.core.Tween | null = null,
    extractedDevice: THREE.Object3D | null = null;
  const deviceHighlightMaterial = new THREE.LineBasicMaterial({ color: 0x65e4ff });
  const alarmLedMaterial = new THREE.MeshBasicMaterial({ color: 0xd36a74 });
  const cabinetLampMaterial = new THREE.MeshBasicMaterial({ color: 0xc7f5ff });
  const levelMaterials = Object.fromEntries(
    alarmLevels.map((level) => [
      level.key,
      {
        outline: new THREE.LineBasicMaterial({ color: level.color }),
        body: new THREE.MeshStandardMaterial({
          color: level.color,
          emissive: level.color,
          emissiveIntensity: 0.08,
          metalness: 0.35,
          roughness: 0.5,
        }),
        led: new THREE.MeshBasicMaterial({ color: level.color }),
        fronts: Object.fromEntries(
          ['server', 'ports'].map((kind) => [
            kind,
            new THREE.MeshStandardMaterial({
              map: hardwareMats[kind].map,
              color: level.color,
              emissive: level.color,
              emissiveIntensity: 0.05,
              roughness: 0.48,
              metalness: 0.4,
            }),
          ]),
        ),
      },
    ]),
  );
  const modelControls = new Map<string, SceneInput['controls'][string]>(),
    modelDeviceAlarms = new Map<string, NonNullable<SceneInput['devices'][number]['alarm']>>();
  function cabinetSettings(id: string) {
    return modelControls.get(id) || { lighting: true, fans: true, locked: false, power: true };
  }
  function updateDeviceVisual(unit: THREE.Object3D) {
    if (!inspected) return;
    const alarm = modelDeviceAlarms.get(unit.userData.inventoryId),
      power = cabinetSettings(inspected.id).power,
      materials = alarm ? levelMaterials[alarm.severity] : null;
    renderable(unit, 'deviceBody').material = materials ? materials.body : slotMat;
    renderable(unit, 'deviceFront').material = materials
      ? materials.fronts[unit.userData.kind]!
      : hardwareMats[unit.userData.kind];
    child(unit, 'runningLed').visible = power && !alarm;
    const led = renderable(unit, 'alarmLed');
    led.visible = power && Boolean(alarm);
    if (materials) led.material = materials.led;
    const outline = renderable(unit, 'deviceHighlight');
    outline.material = materials ? materials.outline : deviceHighlightMaterial;
    outline.visible = Boolean(alarm) || unit === extractedDevice;
  }
  function setDeviceAlarm(id: string, alarm: SceneInput['devices'][number]['alarm']) {
    if (alarm) modelDeviceAlarms.set(id, alarm);
    else modelDeviceAlarms.delete(id);
    const unit = inspectionClone?.getObjectByName('inventory-' + id);
    if (unit) updateDeviceVisual(unit);
  }
  function applyInspectionControls() {
    if (!inspectionClone || !inspected) return;
    const state = cabinetSettings(inspected.id);
    child(inspectionClone, 'cabinetLighting').visible = state.lighting;
    inspectionClone.traverse((o) => {
      if (o.name === 'runningIndicators') o.visible = state.power;
      if (o.userData.inventoryId) updateDeviceVisual(o);
    });
    host.dataset.lighting = String(state.lighting);
    host.dataset.fans = String(state.fans);
    host.dataset.locked = String(state.locked);
    host.dataset.power = String(state.power);
  }
  function setCabinetControls(id: string, state: SceneInput['controls'][string]) {
    modelControls.set(id, { ...state });
    const rack = rackMap.get(id ?? '');
    if (!rack) return;
    if (state.locked) setDoor(id, false);
    rack.group.traverse((o) => {
      if (o.name === 'runningIndicators') o.visible = state.power;
    });
    if (inspected?.id === id) applyInspectionControls();
  }
  function addCabinetDetails() {
    if (!inspectionClone || !inspected) return;
    const cabinet = inspected,
      clone = inspectionClone;
    const detail = new THREE.Group();
    detail.name = 'cabinetDetails';
    clone.add(detail);
    const hasInventory = topologyNodes.some((device) => device.cabinetId === cabinet.id);
    for (const side of [-1, 1]) {
      box(0.035, 2.7, 0.06, side * 0.55, 1.6, -0.43, silver, detail);
      box(0.055, 2.7, 0.06, side * 0.57, 1.6, 0.18, trim, detail);
      // 沿柜侧布置线缆与理线支架，保留设备向前抽出的路径。
      for (let wire = 0; wire < 4; wire++)
        box(
          0.018,
          2.5,
          0.018,
          side * (0.48 + wire * 0.025),
          1.6,
          -0.45,
          wire % 2 ? blue : gold,
          detail,
        );
      for (let y = 0.5; y < 2.9; y += 0.4) {
        box(0.035, 0.04, 0.3, side * 0.54, y, -0.33, trim, detail);
        if (hasInventory) box(0.025, 0.025, 0.85, side * 0.53, y - 0.09, -0.03, silver, detail);
      }
    }
    if (hasInventory)
      for (let i = 0; i < 6; i++) {
        const y = 0.55 + i * 0.4;
        box(1.08, 0.022, 0.88, 0, y - 0.14, -0.02, trim, detail);
        // 未登记的位置使用封板，封板不计为设备资产。
        if (i >= topologyNodes.filter((device) => device.cabinetId === cabinet.id).length)
          box(1.05, 0.23, 0.045, 0, y, 0.44, trim, detail);
        box(1.03, 0.055, 0.045, 0, y + 0.17, 0.44, black, detail);
      }
    box(1.06, 0.13, 0.13, 0, 0.24, 0.37, trim, detail);
    const lamps = new THREE.Group();
    lamps.name = 'cabinetLighting';
    detail.add(lamps);
    for (const side of [-1, 1])
      box(0.027, 2.55, 0.022, side * 0.563, 1.62, 0.225, cabinetLampMaterial, lamps);
    const light = new THREE.PointLight(0xb9eaff, 9, 4, 2);
    light.position.set(0, 2.8, 0.2);
    lamps.add(light);
    // 顶部三台风扇，叶片绕前后轴旋转。
    for (const x of [-0.36, 0, 0.36]) {
      box(0.31, 0.31, 0.11, x, 2.95, 0.28, black, detail);
      const fan = new THREE.Group();
      fan.userData.cabinetFan = true;
      fan.position.set(x, 2.95, 0.35);
      detail.add(fan);
      for (let blade = 0; blade < 5; blade++) {
        const arm = new THREE.Group();
        arm.rotation.z = (blade * Math.PI * 2) / 5;
        fan.add(arm);
        box(0.065, 0.125, 0.012, 0, 0.06, 0, silver, arm);
      }
      box(0.065, 0.065, 0.025, 0, 0, 0.02, trim, fan);
    }
  }

  const deviceEdges = new THREE.EdgesGeometry(boxGeometry(1.09, 0.25, 0.92));
  function resetExtractedDevice() {
    extractionTween?.kill();
    extractionTween = null;
    if (extractedDevice) {
      extractedDevice.position.z = 0;
      renderable(extractedDevice, 'deviceHighlight').visible = Boolean(
        modelDeviceAlarms.get(extractedDevice.userData.inventoryId),
      );
    }
    extractedDevice = null;
    host.dataset.extractedDevice = '';
  }
  function returnDevice() {
    extractionTween?.kill();
    if (!extractedDevice) return;
    const device = extractedDevice;
    extractionTween = gsap.to(device.position, {
      z: 0,
      duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 0.6,
      onComplete: resetExtractedDevice,
    });
  }
  function extractDevice(id: string) {
    const device = inspectionClone?.getObjectByName('inventory-' + id);
    if (!device || !inspected || cabinetSettings(inspected.id).locked) return false;
    resetExtractedDevice();
    extractedDevice = device;
    renderable(device, 'deviceHighlight').visible = true;
    setDoor(inspected.id, true);
    host.dataset.extractedDevice = id;
    extractionTween = gsap.to(device.position, {
      z: 1.65,
      delay: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 0.65,
      duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 0.8,
      ease: 'power2.inOut',
    });
    return true;
  }
  function addInventoryModels() {
    if (!inspectionClone || !inspected) return;
    const cabinet = inspected,
      clone = inspectionClone;
    if (options.data?.platform) child(clone, 'decorativeHardware').visible = false;
    const devices = topologyNodes.filter((device) => device.cabinetId === cabinet.id);
    if (!devices.length) return;
    child(inspectionClone, 'decorativeHardware').visible = false;
    devices.forEach((device, index) => {
      const unit = new THREE.Group();
      unit.name = 'inventory-' + device.id;
      unit.userData.inventoryId = device.id;
      unit.userData.kind = device.layer === 'terminal' ? 'server' : 'ports';
      unit.position.y = 0.55 + index * 0.4;
      clone.add(unit);
      box(1.07, 0.23, 0.9, 0, 0, -0.02, slotMat, unit).name = 'deviceBody';
      const front = new THREE.Mesh(
        planeGeo,
        hardwareMats[device.layer === 'terminal' ? 'server' : 'ports'],
      );
      front.name = 'deviceFront';
      front.position.z = 0.436;
      unit.add(front);
      [-0.51, 0.51].forEach((x) => box(0.025, 0.09, 0.026, x, 0, 0.46, silver, unit));
      box(0.04, 0.04, 0.025, 0.46, 0, 0.47, softMint, unit).name = 'runningLed';
      box(0.04, 0.04, 0.025, 0.39, 0, 0.47, alarmLedMaterial, unit).name = 'alarmLed';
      const outline = new THREE.LineSegments(deviceEdges, deviceHighlightMaterial);
      outline.name = 'deviceHighlight';
      outline.visible = false;
      unit.add(outline);
    });
  }
  function setInventory(devices: SceneInput['inventory']) {
    topologyNodes = devices;
    if (inspected) inspectDevice(inspected.id);
  }
  function showRoom() {
    resetExtractedDevice();
    if (inspectionClone) inspectionRoot.remove(inspectionClone);
    inspectionClone = null;
    cloneDoor = null;
    inspected = null;
    inspectionRoot.visible = false;
    room.visible = true;
    base.visible = true;
    viewport.classList.remove('inspecting');
    local('zoneLabels').hidden = false;
    host.dataset.device = '';
    host.dataset.doorAngle = '';
    configureCamera(false);
    highlight(null, selectedDeviceId);
  }
  function inspectDevice(id: string) {
    resetExtractedDevice();
    const rack = rackMap.get(id ?? '');
    if (!rack) return;
    targetPin.hidden = true;
    alarmPin.hidden = true;
    closeDetail();
    hoverCard.hidden = true;
    selectedDeviceId = id;
    inspected = rack;
    if (inspectionClone) inspectionRoot.remove(inspectionClone);
    inspectionClone = rack.group.clone(true);
    inspectionClone.position.set(0, 0, 0);
    child(inspectionClone, 'selectionOutline').visible = false;
    cloneDoor = inspectionClone.getObjectByName('doorPivot') ?? null;
    addInventoryModels();
    addCabinetDetails();
    applyInspectionControls();
    inspectionRoot.add(inspectionClone);
    inspectionRoot.visible = true;
    room.visible = false;
    base.visible = false;
    viewport.classList.add('inspecting');
    local('zoneLabels').hidden = true;
    host.dataset.device = id;
    configureCamera(true);
  }
  function setExpanded(value: boolean) {
    expanded = value;
    controls.enabled = value;
    closeDetail();
    hovered = null;
    hoverCard.hidden = true;
    if (!value) selectedDeviceId = null;
    showRoom();
  }
  function setDoor(id: string, open: boolean) {
    if (open && cabinetSettings(id).locked) return;
    if (!open && inspected?.id === id) resetExtractedDevice();
    const rack = rackMap.get(id ?? '');
    if (rack) rack.doorOpen = open;
  }
  function setAlarm(id: string, alarm: SceneInput['devices'][number]['alarm']) {
    const rack = rackMap.get(id ?? '');
    if (!rack) return;
    rack.alarm = alarm;
    rack.highlight.material.color.set(alarm ? alarmColors[alarm.severity] : 0x55dfff);
    rack.stateMat.color.set(alarm ? alarmColors[alarm.severity] : 0x39c7ff);
    rack.alarmVisual.visible = Boolean(alarm);
    for (const mesh of [rack.alarmOutline, rack.alarmEdge, rack.alarmFill])
      mesh.material.color.set(alarm ? alarmColors[alarm.severity] : alarmColors.critical);
    if (inspected?.id === id) {
      const visual = inspectionClone?.getObjectByName('activeAlarm');
      if (visual) visual.visible = Boolean(alarm);
    }
    if (markedId === id) markTarget(id);
    projectLabels();
  }
  const motionQuery = matchMedia('(prefers-reduced-motion: reduce)');
  let reduced = motionQuery.matches;
  motionQuery.addEventListener('change', () => (reduced = motionQuery.matches), {
    signal: abort.signal,
  });
  let previous = 0;
  const patrol = createPatrol({ parent: room, host, camera, viewport });
  function animate(t: number) {
    if (disposed) return;
    frame = requestAnimationFrame(animate);
    if (document.hidden || t - previous < 33) return;
    const delta = Math.min((t - previous) / 1000, 0.1);
    previous = t;
    patrol.update(delta, reduced, !inspected);
    if (expanded) {
      controls.update();
    }
    for (const rack of racks) {
      if (rack.alarm) {
        const breath = reduced ? 0.5 : (1 - Math.cos((t * Math.PI) / 2000)) * 0.5;
        rack.alarmEdge.material.opacity = 0.48 + breath * 0.24;
        rack.alarmOutline.material.opacity = 0.3 + breath * 0.17;
        rack.alarmFill.material.opacity = 0.13 + breath * 0.035;
      }
      const desired = rack.doorOpen ? -Math.PI * 0.61 : 0;
      rack.doorAngle = reduced ? desired : THREE.MathUtils.damp(rack.doorAngle, desired, 9, delta);
      if (Math.abs(rack.doorAngle - desired) < 0.001) rack.doorAngle = desired;
      rack.door.rotation.y = rack.doorAngle;
    }
    if (cloneDoor && inspected) {
      cloneDoor.rotation.y = inspected.doorAngle;
      host.dataset.doorAngle = inspected.doorAngle.toFixed(3);
    }
    if (inspectionClone && inspected && cabinetSettings(inspected.id).fans && !reduced)
      inspectionClone.traverse((o) => {
        if (o.userData.cabinetFan) {
          o.rotation.z += delta * 12;
          host.dataset.fanAngle = o.rotation.z.toFixed(3);
        }
      });
    if (!reduced) {
      lights.forEach((l, i) => {
        l.visible = Math.sin(t * 0.0011 + i * 2.3) > -0.9;
      });
      rotors.forEach((rotor) => (rotor.group.rotation.y += rotor.speed * delta));
      host.dataset.baseRotation = rotors[0].group.rotation.y.toFixed(4);
    }
    if (markedId) {
      const rack = rackMap.get(markedId ?? '');
      if (rack) {
        rack.highlight.visible = true;
        rack.highlight.material.opacity = reduced ? 0.94 : 0.91 + Math.sin(t * 0.003) * 0.07;
        targetGlow.material.opacity = rack.alarm?.severity
          ? reduced
            ? 0.045
            : 0.045 + Math.sin(t * 0.00157) * 0.015
          : reduced
            ? 0.22
            : 0.22 + Math.sin(t * 0.003) * 0.035;
      }
    }
    if (!inspected) projectLabels();
    renderer.render(scene, camera);
    host.dataset.drawCalls = String(renderer.info.render.calls);
  }
  renderer.domElement.addEventListener(
    'webglcontextlost',
    (e) => {
      e.preventDefault();
      local('sceneError').textContent = '三维图形上下文已中断，请刷新页面恢复。';
      local('sceneError').hidden = false;
    },
    { signal: abort.signal },
  );
  frame = requestAnimationFrame(animate);
  host.dataset.rackCount = String(racks.length);
  document.addEventListener(
    'keydown',
    (e) => {
      if (e.key === 'Escape' && !local('zoneDetail').hidden) {
        e.preventDefault();
        closeDetail();
      }
    },
    { signal: abort.signal },
  );
  function setMonitoring(devices: SceneInput['devices'], zones: SceneInput['zones']) {
    for (const device of devices) {
      const current = d.devices.find((item) => item.id === device.id);
      if (current) Object.assign(current, device);
      setAlarm(device.id, device.alarm);
    }
    for (const zone of zones) {
      const label = labels.find((item) => item.zone.id === zone.id);
      if (!label) continue;
      Object.assign(label.zone, zone);
      label.el.querySelector('strong')!.textContent =
        zone.temperature === '未采集' ? '未采集' : zone.temperature + ' ℃';
      label.el.setAttribute('aria-label', `${zone.id}区 ${zone.temperature} 查看区域信息`);
    }
    if (selected) {
      const zone = labels.find((item) => item.zone.id === selected)?.zone;
      if (zone) openZone(zone);
    }
  }
  return {
    setMonitoring,
    locateFacility,
    clearFacility,
    setInventory,
    setCabinetControls,
    setDeviceAlarm,
    extractDevice,
    returnDevice,
    patrol,
    resetView,
    dispose() {
      clearFacility();
      resetExtractedDevice();
      deviceEdges.dispose();
      deviceHighlightMaterial.dispose();
      patrol.cancel();
      viewTween?.kill();
      disposed = true;
      cancelAnimationFrame(frame);
      observer.disconnect();
      abort.abort();
      controls.dispose();
      const geometries = new Set<THREE.BufferGeometry>(),
        materials = new Set<THREE.Material>(),
        textures = new Set<THREE.Texture>();
      scene.traverse((o) => {
        if (!(o instanceof THREE.Mesh || o instanceof THREE.Line || o instanceof THREE.Points))
          return;
        if (o.geometry) geometries.add(o.geometry);
        for (const m of Array.isArray(o.material) ? o.material : o.material ? [o.material] : []) {
          materials.add(m);
          for (const v of Object.values(m)) if (v instanceof THREE.Texture) textures.add(v);
        }
      });
      geometries.forEach((g) => g.dispose());
      textures.forEach((t) => t.dispose());
      materials.forEach((m) => m.dispose());
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
      root
        .querySelectorAll(
          '.zone-label,.zone-connectors,.active-alarm-pin,.patrol-target-pin,.patrol-label',
        )
        .forEach((el) => el.remove());
    },
    markTarget,
    resize,
    setExpanded,
    inspectDevice,
    showRoom,
    setDoor,
    setAlarm,
    closeZone: () => closeDetail(),
    getState: () => ({
      rackCount: racks.length,
      expanded,
      inspected: inspected?.id,
      inventory: inspectionClone
        ? inspectionClone.children
            .filter((o) => o.userData.inventoryId)
            .map((o) => ({ id: o.userData.inventoryId, kind: o.userData.kind }))
        : [],
      devices: racks.map((r) => ({
        id: r.id,
        zone: r.zone,
        doorOpen: r.doorOpen,
        doorAngle: r.doorAngle,
        alarm: r.alarm?.severity || null,
        alarmVisible: r.alarmVisual.visible,
      })),
      controlsEnabled: controls.enabled,
      camera: camera.position.toArray(),
      target: controls.target.toArray(),
      minDistance: controls.minDistance,
      maxDistance: controls.maxDistance,
      polarAngle: controls.getPolarAngle(),
      azimuthAngle: controls.getAzimuthalAngle(),
      drawCalls: renderer.info.render.calls,
      triangles: renderer.info.render.triangles,
      highlightedZone: viewport.dataset.highlight,
    }),
  };
}
