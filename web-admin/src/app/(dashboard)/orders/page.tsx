import { redirect } from 'next/navigation';

export default async function OrdersPage({
  searchParams,
}: PageProps<'/orders'>) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(await searchParams)) {
    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, item));
    } else if (value !== undefined) {
      params.set(key, value);
    }
  }
  params.set('tab', 'orders');
  redirect('/dispatch?' + params.toString());
}
