import { AddAppPage } from "../../lib/app-list";

export default function AddExactApp() {
  return <AddAppPage list="exact" apps={({ exactApps }) => exactApps} />;
}
