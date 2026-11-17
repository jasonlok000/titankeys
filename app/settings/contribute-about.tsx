import { Screen, Section, useSnapshot } from "ink";
import { sharedToday } from "../../lib/connected";
import { contributeText as text } from "../../lib/connected-text";
import { WhatIsSent } from "../../lib/contribute";
import { Note, Notes } from "../../lib/note";
import { SHARED_COUNTS, SHARED_PARTS, meaningOf } from "../../lib/sharing-counts";

/**
 * Settings > Sharing > What's shared (#96): what is sent and what never is, today's totals as they would go, and every daily total
 * with what it means. The list is the code's own (lib/sharing-counts.ts), so the user always sees what's counted.
 */
export default function ContributeAbout() {
  const shared = useSnapshot(sharedToday);
  const today = shared.status === "ready" ? shared.data.today : null;
  const counts = Object.entries(today?.counts ?? {});
  return <Screen title={text.about}>
    <WhatIsSent />
    <Section title={text.todayTitle}>
      {counts.length === 0 ? <Note>{text.todayEmpty}</Note>
        : <Notes lines={counts.map(([key, value]) => `${value} · ${meaningOf(key) ?? key} (${key})`)} />}
      {today?.guide && <Note>{`${text.guideTitle}: ${today.guide.furthest}${today.guide.finished ? ", finished" : ""}`}</Note>}
      {today?.settings && <Note>{`${text.settingsTitle}: ${today.settings.set.join(", ") || "none"}. ${text.settingsNote}`}</Note>}
      {shared.status === "ready" && shared.data.waitingDays > 0 && <Note>{text.waitingDays(shared.data.waitingDays)}</Note>}
    </Section>
    <Section title={text.countsTitle}>
      <Note>{text.countsNote}</Note>
    </Section>
    {SHARED_COUNTS.map(group => <Section key={group.title} title={group.title}>
      <Notes lines={group.counts.map(count => `${count.meaning} (${count.key})`)} />
    </Section>)}
    <Section title="Also">
      <Notes lines={SHARED_PARTS.map(part => part.meaning)} />
    </Section>
  </Screen>;
}
