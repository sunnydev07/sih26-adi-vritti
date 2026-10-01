import { redirect } from "next/navigation";

export default function Home() {
  // Not /dashboard: an unauthenticated redirect at the root URL walked every
  // visitor straight into the officer console. Signed-in officers are already sent
  // onward by the dashboard guard; everyone else starts at sign-in.
  redirect("/login");
}