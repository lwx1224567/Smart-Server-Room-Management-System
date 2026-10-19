import * as THREE from './vendor/three.module.js';
import { mockData as d } from '../demo/mockData.js';

export interface PatrolDevice {
  id: string;
  position: number[];
}
export interface PatrolResult {
  cancelled: boolean;
  deviceId?: string;
  position?: number[];
  route?: number[][];
}
// Waypoints stay in the front/back corridors and between cabinet columns.
export function createPatrol({
  parent,
  host,
  camera,
  viewport,
}: {
  parent: THREE.Object3D;
  host: HTMLElement;
  camera: THREE.Camera;
  viewport: HTMLElement;
}) {
  const cfg = d.assistant,
    person = new THREE.Group();
  person.name = 'maintenanceTechnician';
  parent.add(person);
  const material = (color: THREE.ColorRepresentation) =>
    new THREE.MeshStandardMaterial({ color, roughness: 0.57, metalness: 0.12 });
  const uniform = material(0x368ebc),
    navy = material(0x163750),
    skin = material(0xe4b88d),
    helmet = material(0xffc55a),
    boots = material(0x26343e),
    reflective = new THREE.MeshBasicMaterial({ color: 0xa5e9e6 });
  const cube = new THREE.BoxGeometry(1, 1, 1),
    sphere = new THREE.SphereGeometry(1, 16, 12);
  function part(
    geometry: THREE.BufferGeometry,
    mat: THREE.Material,
    scale: number[],
    position: number[],
    group = person,
  ) {
    const mesh = new THREE.Mesh(geometry, mat);
    mesh.scale.fromArray(scale);
    mesh.position.fromArray(position);
    mesh.castShadow = true;
    group.add(mesh);
    return mesh;
  }
  part(cube, uniform, [0.38, 0.47, 0.24], [0, 1.06, 0]);
  part(cube, navy, [0.32, 0.17, 0.24], [0, 0.77, 0]);
  part(cube, reflective, [0.39, 0.045, 0.25], [0, 1.04, 0]);
  part(cube, reflective, [0.05, 0.35, 0.25], [-0.11, 1.1, 0]);
  part(cube, reflective, [0.05, 0.35, 0.25], [0.11, 1.1, 0]);
  part(sphere, skin, [0.15, 0.18, 0.14], [0, 1.47, 0]);
  part(sphere, helmet, [0.185, 0.105, 0.18], [0, 1.6, 0]);
  part(cube, helmet, [0.37, 0.025, 0.36], [0, 1.56, 0.025]);
  part(cube, navy, [0.14, 0.07, 0.018], [0, 1.48, 0.133]);
  const legs: THREE.Group[] = [],
    arms: THREE.Group[] = [];
  for (const x of [-0.105, 0.105]) {
    const limb = new THREE.Group();
    limb.position.set(x, 0.77, 0);
    person.add(limb);
    part(cube, navy, [0.13, 0.65, 0.15], [0, -0.325, 0], limb);
    part(cube, boots, [0.16, 0.12, 0.24], [0, -0.71, 0.045], limb);
    legs.push(limb);
  }
  for (const x of [-0.215, 0.215]) {
    const arm = new THREE.Group();
    arm.position.set(x, 1.26, 0);
    person.add(arm);
    part(cube, uniform, [0.12, 0.37, 0.13], [0, -0.18, 0], arm);
    part(sphere, skin, [0.064, 0.075, 0.061], [0, -0.42, 0], arm);
    arms.push(arm);
  }
  part(cube, navy, [0.14, 0.27, 0.04], [0.21, 0.93, 0.14]);
  part(
    cube,
    new THREE.MeshBasicMaterial({ color: 0x61d6ed }),
    [0.11, 0.2, 0.045],
    [0.21, 0.95, 0.15],
  );
  const shadow = new THREE.Mesh(
    new THREE.CircleGeometry(0.31, 24),
    new THREE.MeshBasicMaterial({
      color: 0x061827,
      transparent: true,
      opacity: 0.23,
      depthWrite: false,
    }),
  );
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.y = 0.095;
  parent.add(shadow);
  person.position.fromArray(cfg.startPosition);
  const label = document.createElement('div');
  label.id = 'patrolLabel';
  label.className = 'patrol-label';
  label.innerHTML = '<i></i><span>小维 · 例行巡检</span>';
  viewport.appendChild(label);
  const routeLine = new THREE.Line(
    new THREE.BufferGeometry(),
    new THREE.LineBasicMaterial({ color: 0x48dec4, transparent: true, opacity: 0.67 }),
  );
  parent.add(routeLine);
  routeLine.visible = false;
  let mode = 'patrol',
    route = cfg.patrolRoute.map((p) => new THREE.Vector3(...p)),
    index = 0,
    resolveTask: ((result: PatrolResult) => void) | null = null,
    targetDevice: PatrolDevice | null = null,
    walkTime = 0,
    walked = 0,
    total = 1;
  let routeHistory: number[][] = [];
  function setLabel(text: string) {
    label.querySelector('span')!.textContent = text;
  }
  function publish() {
    host.dataset.patrolMode = mode;
    host.dataset.patrolPosition = person.position
      .toArray()
      .map((v) => v.toFixed(3))
      .join(',');
    host.dataset.patrolTarget = targetDevice?.id || '';
    host.dataset.patrolProgress = String(Math.min(1, walked / total).toFixed(3));
  }
  function cancel() {
    if (resolveTask) {
      resolveTask({ cancelled: true });
      resolveTask = null;
    }
    targetDevice = null;
    routeLine.visible = false;
    mode = 'paused';
    setLabel('小维 · 待命');
    publish();
  }
  function pause() {
    cancel();
  }
  function toFront() {
    const pos = person.position,
      aisles = [-5.01, -2.61, -0.21, 2.19, 4.59, 6.99];
    const x =
      pos.z < -5.45
        ? aisles.reduce((a, b) => (Math.abs(b - pos.x) < Math.abs(a - pos.x) ? b : a))
        : pos.x;
    return pos.z < -5.45
      ? [new THREE.Vector3(x, 0.1, -5.7), new THREE.Vector3(x, 0.1, 6.2)]
      : [new THREE.Vector3(x, 0.1, 6.2)];
  }
  function resume() {
    cancel();
    mode = 'patrol';
    route = [...toFront(), ...cfg.patrolRoute.map((p) => new THREE.Vector3(...p))];
    index = 0;
    setLabel('小维 · 例行巡检');
    publish();
  }
  function visit(device: PatrolDevice): Promise<PatrolResult> {
    cancel();
    targetDevice = device;
    mode = 'visiting';
    const aisle = device.position[0] + 0.99;
    route = [
      ...toFront(),
      new THREE.Vector3(aisle, 0.1, 6.2),
      new THREE.Vector3(aisle, 0.1, device.position[2] + 0.45),
    ];
    index = 0;
    walked = 0;
    routeHistory = [person.position.toArray(), ...route.map((p) => p.toArray())];
    total = 0;
    let prev = person.position;
    for (const point of route) {
      total += prev.distanceTo(point);
      prev = point;
    }
    total = Math.max(total, 0.001);
    routeLine.geometry.dispose();
    routeLine.geometry = new THREE.BufferGeometry().setFromPoints(
      routeHistory.map((p) => new THREE.Vector3(p[0], 0.12, p[2])),
    );
    routeLine.visible = true;
    setLabel(`小维 → ${device.id}`);
    publish();
    return new Promise<PatrolResult>((resolve) => (resolveTask = resolve));
  }
  function arrive() {
    if (!targetDevice) return;
    mode = 'arrived';
    routeLine.visible = false;
    setLabel(`小维 · 已到达 ${targetDevice.id}`);
    person.lookAt(
      new THREE.Vector3(targetDevice.position[0], person.position.y, targetDevice.position[2]),
    );
    publish();
    const done = resolveTask;
    resolveTask = null;
    done?.({
      cancelled: false,
      deviceId: targetDevice.id,
      position: person.position.toArray(),
      route: routeHistory,
    });
  }
  function update(delta: number, reduced: boolean, visible: boolean) {
    label.hidden = !visible;
    person.visible = visible;
    shadow.visible = visible;
    if (!visible) return;
    let moving = false;
    if (mode === 'visiting' || mode === 'patrol') {
      if (reduced && mode === 'visiting') {
        person.position.copy(route.at(-1)!);
        walked = total;
        arrive();
      } else if (!reduced && route.length) {
        const next = route[index],
          diff = next.clone().sub(person.position),
          distance = diff.length(),
          step = cfg.walkSpeed * delta;
        moving = true;
        person.rotation.y = Math.atan2(diff.x, diff.z);
        if (distance <= step) {
          person.position.copy(next);
          walked += distance;
          index++;
          if (index >= route.length) {
            if (mode === 'visiting') arrive();
            else {
              route = cfg.patrolRoute.map((p) => new THREE.Vector3(...p));
              index = 0;
            }
          }
        } else {
          person.position.addScaledVector(diff, step / distance);
          walked += step;
        }
      }
    }
    walkTime += moving ? delta * 4.6 : 0;
    legs.forEach((p, i) => (p.rotation.x = moving ? Math.sin(walkTime + i * Math.PI) * 0.42 : 0));
    arms.forEach((p, i) => (p.rotation.x = moving ? -Math.sin(walkTime + i * Math.PI) * 0.28 : 0));
    shadow.position.x = person.position.x;
    shadow.position.z = person.position.z;
    publish();
    const screen = person.position
      .clone()
      .add(new THREE.Vector3(0, 1.95, 0))
      .project(camera);
    label.style.left = `${(screen.x * 0.5 + 0.5) * host.clientWidth}px`;
    label.style.top = `${(-screen.y * 0.5 + 0.5) * host.clientHeight}px`;
    label.hidden = screen.z > 1 || screen.z < -1;
  }
  publish();
  return {
    update,
    visit,
    cancel,
    pause,
    resume,
    getState: () => ({
      mode,
      target: targetDevice?.id,
      position: person.position.toArray(),
      route: routeHistory,
      progress: Math.min(1, walked / total),
    }),
  };
}
