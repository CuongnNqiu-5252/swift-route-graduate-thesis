'use client';
import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import { MapContainer, Marker, Polyline, TileLayer } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

const icon = (color: string) => L.divIcon({ className: '', html: `<div style="width:18px;height:18px;border-radius:50%;background:${color};border:3px solid white;box-shadow:0 2px 8px #0f172a55"></div>`, iconSize:[18,18], iconAnchor:[9,9] });
export default function OrderTrackingMap({ pickup, delivery, driver }: { pickup:[number,number]; delivery:[number,number]; driver?:[number,number] }) {
  const points = driver ? [pickup, driver, delivery] : [pickup, delivery];
  const center = driver || pickup;
  return <MapContainer center={center} zoom={13} className="h-80 w-full"><TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/><Polyline positions={points} pathOptions={{ color:'#2563eb', weight:4, dashArray:'8 7' }}/><Marker position={pickup} icon={icon('#0f766e')}/>{driver && <SmoothDriverMarker position={driver}/>}<Marker position={delivery} icon={icon('#e11d48')}/></MapContainer>;
}

function SmoothDriverMarker({ position }: { position: [number, number] }) {
  const [displayed, setDisplayed] = useState(position);
  const displayedRef = useRef(displayed);
  useEffect(() => { displayedRef.current = displayed; }, [displayed]);
  useEffect(() => {
    const from = displayedRef.current;
    const startedAt = performance.now();
    let frame = 0;
    const animate = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / 900);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplayed([from[0] + (position[0] - from[0]) * eased, from[1] + (position[1] - from[1]) * eased]);
      if (progress < 1) frame = requestAnimationFrame(animate);
    };
    frame = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frame);
  }, [position]);
  return <Marker position={displayed} icon={icon('#2563eb')} />;
}
