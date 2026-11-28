import type { Metadata } from 'next';
import { PublicTrackingView } from '@/components/orders/public-tracking-view';

export const metadata: Metadata = {
  title: 'SwiftRoute — Tra cứu vận đơn',
  robots: {
    index: false,
    follow: false,
  },
};

export default async function PublicTrackingPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;
  return <PublicTrackingView token={decodeURIComponent(token)} />;
}
