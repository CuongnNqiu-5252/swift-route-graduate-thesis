'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { AlertTriangle, BarChart3, ChevronLeft, ChevronRight, LayoutDashboard, MapPin, Menu, PackageSearch, Route, Settings, Truck, UserRound, Users, WalletCards, X } from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/utils';

const navItems = [
  { href: '/', label: 'Tổng quan', icon: LayoutDashboard },
  { href: '/orders', label: 'Đơn hàng', icon: PackageSearch },
  { href: '/dispatch', label: 'Điều phối', icon: Route },
  { href: '/operations', label: 'Sự cố & COD', icon: AlertTriangle },
  { href: '/drivers', label: 'Tài xế', icon: Users },
  { href: '/fleet', label: 'Phương tiện', icon: Truck },
  { href: '/locations', label: 'Điểm lấy hàng', icon: MapPin },
  { href: '/settlements', label: 'Đối soát tài xế', icon: WalletCards },
  { href: '/members', label: 'Nhân sự', icon: Users },
  { href: '/analytics', label: 'Phân tích', icon: BarChart3 },
  { href: '/organizations', label: 'Tổ chức', icon: Users },
  { href: '/onboarding', label: 'Thiết lập doanh nghiệp', icon: Settings },
  { href: '/settings', label: 'Cài đặt', icon: Settings },
  { href: '/profile', label: 'Hồ sơ cá nhân', icon: UserRound },
];

export function Sidebar() {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  function toggleCollapsed(){ setCollapsed(value=>{ const next=!value; localStorage.setItem('sidebar-collapsed',String(next)); return next; }); }
  const mobileItems = navItems.slice(0, 5);
  return (
    <aside className={cn('fixed inset-x-0 bottom-0 z-40 flex h-16 flex-row border-t border-slate-200 bg-white transition-[width] lg:static lg:h-dvh lg:shrink-0 lg:flex-col lg:border-0 lg:bg-[#eef1f4]',collapsed?'lg:w-20':'lg:w-60')}>
      <div className="hidden h-16 shrink-0 items-center gap-3 px-6 lg:flex">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600"><Truck size={18} className="text-white" /></div>
        {!collapsed&&<div><p className="text-sm font-bold text-gray-900">SwiftRoute</p><p className="text-xs text-gray-500">Business Workspace</p></div>}
      </div>
      <nav className="flex flex-1 items-center justify-around overflow-x-auto px-1 lg:block lg:overflow-y-auto lg:px-3 lg:py-4" aria-label="Điều hướng workspace">
        <ul className="hidden space-y-1 lg:block">{navItems.map(({ href, label, icon: Icon }) => {
          const active = href === '/' ? pathname === '/' : pathname.startsWith(href);
          return <li key={href}><Link href={href} title={label} aria-label={label} aria-current={active ? 'page' : undefined} className={cn('flex min-h-11 items-center justify-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',!collapsed&&'lg:justify-start', active ? 'bg-white text-slate-950' : 'text-slate-600 hover:bg-white/60 hover:text-slate-950')}><Icon size={18} className="shrink-0" />{!collapsed&&<span className="hidden lg:inline">{label}</span>}</Link></li>;
        })}</ul>
        <ul className="flex w-full items-center justify-around lg:hidden">{mobileItems.map(({ href, label, icon: Icon }) => <li key={href}><Link href={href} aria-label={label} aria-current={(href === '/' ? pathname === '/' : pathname.startsWith(href)) ? 'page' : undefined} className="flex min-h-12 min-w-12 flex-col items-center justify-center gap-1 text-[10px] text-slate-600"><Icon size={19}/><span>{label}</span></Link></li>)}<li><button type="button" aria-label={menuOpen ? 'Đóng menu' : 'Mở thêm mục'} aria-expanded={menuOpen} onClick={() => setMenuOpen(value => !value)} className="flex min-h-12 min-w-12 flex-col items-center justify-center gap-1 text-[10px] text-slate-600">{menuOpen ? <X size={19}/> : <Menu size={19}/>}<span>Thêm</span></button></li></ul>
      </nav>
      {menuOpen && <div className="absolute inset-x-0 bottom-16 z-50 max-h-[65dvh] overflow-y-auto border-t border-slate-200 bg-white p-3 shadow-lg lg:hidden"><ul className="grid grid-cols-2 gap-2">{navItems.slice(5).map(({href,label,icon:Icon})=><li key={href}><Link onClick={()=>setMenuOpen(false)} href={href} className="flex min-h-12 items-center gap-3 rounded-lg px-3 text-sm text-slate-700 hover:bg-slate-100"><Icon size={18}/>{label}</Link></li>)}</ul></div>}
      <div className="hidden p-3 lg:block"><button type="button" onClick={toggleCollapsed} aria-label={collapsed?'Mở rộng sidebar':'Thu gọn sidebar'} className="flex min-h-10 w-full items-center justify-center gap-2 rounded-lg text-sm text-slate-600 hover:bg-white">{collapsed?<ChevronRight size={17}/>:<><ChevronLeft size={17}/><span>Thu gọn</span></>}</button></div>
    </aside>
  );
}
