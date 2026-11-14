import { redirect } from 'next/navigation';

/** Redirect legacy standalone settlement route into the unified Drivers workspace. */
export default function SettlementsRedirectPage() {
  redirect('/drivers?tab=settlements');
}
