import { Row, Screen, Section, openURL } from "ink";
import { arrowOutward, close } from "ink/icons";
import { appReleaseNotes } from "./release-notes-format";
import { releaseNotes, releaseNotesURL } from "./release-notes";

/** The current version's short release notes; the link opens the full notes. With `onClose` it opens by itself after an update; otherwise it has a back arrow. */
export function WhatsNew({ version, onClose }: { version: string; onClose?: () => void }) {
  const { notes, fixes } = appReleaseNotes(releaseNotes);
  return <Screen title="What's new" subtitle={onClose ? `Atlas updated to ${version}.` : `Version ${version}`}
    rightAction={onClose && { icon: close, onPress: onClose }}>
    {notes.length > 0 && <Section title="New">
      {notes.map(note => <Row key={note.title} title={note.title} subtitle={note.detail} wrapSubtitle />)}
    </Section>}
    {fixes && <Section title="Fixed">
      <Row title="Bug fixes" subtitle="This update fixes some problems." wrapSubtitle />
    </Section>}
    <Row title="Full release notes" endIcon={arrowOutward} onPress={() => void openURL(releaseNotesURL(version))} />
  </Screen>;
}
