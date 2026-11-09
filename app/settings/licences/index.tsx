import { Row, Screen } from "ink";
import { arrowOutward } from "ink/icons";

const licences = [
  { title: "Atlas", subtitle: "GPL-3.0. TitanKeys is a modified version of Atlas.", url: "https://github.com/juleshall4/atlas" },
  { title: "Pastiera", subtitle: "GPL-3.0. TitanKeys uses its Enter key rules.", url: "https://github.com/palsoftware/pastiera" },
  { title: "FUTO Swipe models", subtitle: "FUTO Model Weights License", url: "https://huggingface.co/futo-org/futo-swipe" },
  { title: "FUTO Swipe library", subtitle: "GPL-3.0. TitanKeys uses a patched copy of its Android code.", url: "https://gitlab.futo.org/keyboard/swipe-library" },
  { title: "FUTO Keyboard", subtitle: "FUTO Source First License", url: "https://github.com/futo-org/android-keyboard" },
  { title: "FUTO native libraries", subtitle: "ExecuTorch and other dependencies", url: "https://github.com/pytorch/executorch" },
  { title: "Android NDK", subtitle: "LLVM notice", url: "https://llvm.org/LICENSE.txt" },
  { title: "AOSP dictionary", subtitle: "Apache 2.0", url: "https://codeberg.org/Helium314/aosp-dictionaries" },
  { title: "Ink", subtitle: "MIT", url: "https://github.com/No-Scrolling/ink" },
  { title: "Inter", subtitle: "SIL Open Font Licence", url: "https://github.com/rsms/inter" },
  { title: "Remix Icon", subtitle: "Apache 2.0", url: "https://github.com/Remix-Design/RemixIcon" },
  { title: "Unicode data", subtitle: "Unicode License 3.0", url: "https://www.unicode.org/license.txt" },
];

export default function Licences() {
  return <Screen title="Licences">
    {licences.map(licence => <Row key={licence.title} {...licence} endIcon={arrowOutward} />)}
  </Screen>;
}
