import { AddAppPage } from "../../lib/app-list";

export default function AddHiddenBarApp() {
  return <AddAppPage list="hiddenBar" apps={({ hiddenBarApps }) => hiddenBarApps} />;
}
