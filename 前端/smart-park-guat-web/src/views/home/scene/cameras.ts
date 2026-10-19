export type CameraStatus = 'online' | 'offline' | 'error';
export interface RoomCamera {
  id: string;
  zone: string;
  name: string;
  position: [number, number, number];
  url: string;
  status: CameraStatus;
}
// 保留已有摄像头 ID；四角覆盖 A/D/C/E 区，后侧中央摄像头覆盖 B 区，未接入视频源。
export const cameras: RoomCamera[] = [
  {
    id: 'zone-a',
    zone: 'A',
    name: 'A区摄像头',
    position: [-8.65, 3.42, -6.65],
    url: '/videos/monitor-demo.mp4',
    status: 'offline',
  },
  {
    id: 'zone-b',
    zone: 'D',
    name: 'D区摄像头',
    position: [-8.65, 3.42, 6.65],
    url: '/videos/monitor-demo.mp4',
    status: 'offline',
  },
  {
    id: 'zone-c',
    zone: 'C',
    name: 'C区摄像头',
    position: [8.65, 3.42, -6.65],
    url: '/videos/monitor-demo.mp4',
    status: 'offline',
  },
  {
    id: 'zone-d',
    zone: 'E',
    name: 'E区摄像头',
    position: [8.65, 3.42, 6.65],
    url: '/videos/monitor-demo.mp4',
    status: 'offline',
  },
  {
    id: 'camera-b',
    zone: 'B',
    name: 'B区摄像头',
    position: [0, 3.42, -6.65],
    url: '/videos/monitor-demo.mp4',
    status: 'offline',
  },
];
