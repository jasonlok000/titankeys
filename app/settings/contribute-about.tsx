import { Screen } from "ink";
import { contributeText as text } from "../../lib/connected-text";
import { WhatIsSent } from "../../lib/contribute";

/** The disclosure's details again, for a user who already agreed. */
export default function ContributeAbout() {
  return <Screen title={text.about}>
    <WhatIsSent />
  </Screen>;
}
