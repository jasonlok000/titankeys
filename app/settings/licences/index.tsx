import { Row, Screen } from "ink";
import { arrowOutward } from "ink/icons";

const licences = [
  { title: "Atlas", subtitle: "GPL-3.0. TitanKeys is a modified version of Atlas.", url: "https://github.com/juleshall4/atlas" },
  { title: "Pastiera", subtitle: "GPL-3.0. TitanKeys uses its Enter key rules and its autocorrect.", url: "https://github.com/palsoftware/pastiera" },
  { title: "Numen", subtitle: "GPL-3.0. TitanKeys uses its autocorrect, which is a modified version of Pastiera.", url: "https://github.com/jlo-aug/numen" },
  { title: "Simple English Wikipedia", subtitle: "CC BY-SA 4.0. Word pairs for autocorrect.", url: "https://simple.wikipedia.org" },
  { title: "Wikipedia misspellings", subtitle: "CC BY-SA 4.0. Known misspellings for autocorrect.", url: "https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines" },
  { title: "Tatoeba", subtitle: "CC BY 2.0 FR. Word pairs and word triples for autocorrect.", url: "https://tatoeba.org" },
  { title: "Google Books Ngram", subtitle: "CC BY 3.0. Word pairs for autocorrect.", url: "https://github.com/orgtre/google-books-ngram-frequency" },
  { title: "Aalto ITE typing data", subtitle: "CC BY 4.0. Word mistakes for autocorrect.", url: "https://doi.org/10.5281/zenodo.12528163" },
  { title: "SCOWL", subtitle: "SCOWL licence. Known words for autocorrect.", url: "http://wordlist.aspell.net" },
  { title: "US baby names", subtitle: "Public domain. First names for autocorrect.", url: "https://www.ssa.gov/oact/babynames" },
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
