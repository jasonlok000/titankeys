import { useState } from "react";
import { Row, Screen, Section, TextInput, replace, useRouteParams } from "ink";
import { close, search } from "ink/icons";
import { guide, searchGuide, type GuideRow } from "../../lib/guide";

const row = ({ title, text }: GuideRow) => <Row key={title} title={title} subtitle={text} wrapSubtitle />;

export default function Guide() {
  // Opened from the front page, the guide closes back to it, like Settings does.
  const fromHome = useRouteParams().from === "home";
  const closeAction = fromHome ? { icon: close, onPress: () => replace("/") } : undefined;
  const [searching, setSearching] = useState(false);
  const [query, setQuery] = useState("");
  const results = searchGuide(query);
  // Pinned, as in Your words, so the input stays above the results.
  const header = searching
    ? <TextInput value={query} onChange={setQuery} onSubmit={value => value.trim() || setSearching(false)}
      placeholder="Search the guide" action="search" autoFocus
      style={{ border: "none", paddingX: 0, paddingY: 0, minHeight: 50, fontSize: 26, placeholderColour: "muted" }} />
    : <Row title="Search" endIcon={search} onPress={() => setSearching(true)} />;
  return <Screen title="Guide" subtitle="How to use Atlas." rightAction={closeAction} header={header}>
    {results
      ? results.length ? results.map(row) : <Row title="No results" subtitle="Use different words." wrapSubtitle />
      : guide.map(section => <Section key={section.title} title={section.title}>{section.rows.map(row)}</Section>)}
  </Screen>;
}
