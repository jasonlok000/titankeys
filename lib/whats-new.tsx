import { Row, Screen, Section, openURL } from "ink";
import { arrowOutward, close } from "ink/icons";
import { releaseNotes, releaseNotesURL } from "./release-notes";
import { Topic } from "./topic";

/** The current version's release notes. With `onClose` it opens by itself after an update; otherwise it has a back arrow. */
export function WhatsNew({ version, onClose }: { version: string; onClose?: () => void }) {
  return <Screen title="What's new" subtitle={onClose ? `You're on TitanKeys ${version}.` : `Version ${version}`}
    rightAction={onClose && { icon: close, onPress: onClose }}>
    {releaseNotes.sections.map(section => <Section key={section.title} title={section.title}>
      {section.notes.map(note => note.detail
        ? <Topic key={note.title} title={note.title}>{note.detail}</Topic>
        : <Row key={note.title} title={note.title} titleMaxLines={3} />)}
    </Section>)}
    <Row title="Full release notes" endIcon={arrowOutward} onPress={() => void openURL(releaseNotesURL(version))} />
  </Screen>;
}
