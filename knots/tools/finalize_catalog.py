#!/usr/bin/env python3
"""One-time, idempotent editorial migration for the initial release.
The published source of truth remains catalog-a/b/c/d.txt after this review.
Run explicitly; the ordinary build never rewrites its own source catalogs.
"""
from build import SRC, parse_catalog, serialize

NEW = r'''
@double-alpine-butterfly-loop|Double Alpine Butterfly Loop|double-alpine-butterfly-loop-knot|Fixed loop|3|Camping,Specialist|Double butterfly loop
=Make two fixed eyes in the middle of a rope.
+About two metres of soft practice rope; keep hand wraps loose.
!Two eyes are not automatically an equalized anchor. Never use this practice arrangement to suspend a person.
?Two neighbouring turns become the eyes, while the two outside turns form the knot's shoulders.
xPicking up an outside turn instead of the middle pair gives a different structure.
>Lay out four turns::Wrap the rope so four parallel sections cross your palm, keeping everything loose enough to remove easily.
>Identify the eye sections::Position the second and third turns toward your fingertips, with the first and fourth nearer your thumb.
>Lift the middle pair::Pick up the two fingertip-side turns together and draw them out slightly.
>Pass under the outside pair::Fold those two turns around and up beneath the other two sections, following the demonstration. These folds become the two eyes.
>Slide off and dress::Remove your hand before tightening. Adjust the eye sizes, then draw the standing parts apart while keeping all turns orderly.

@spanish-bowline|Spanish Bowline|spanish-bowline-knot|Fixed loop|3|Boating,Specialist|Spanish double loop
=Form two adjacent eyes from a carefully arranged central loop.
+Long soft practice rope on a flat table.
!Do not treat this as a harness, rescue seat or automatically balanced anchor. The loops need careful dressing.
?Each side of the central loop has been drawn through its corresponding side loop.
xPulling only one side through the centre does not complete the pair of eyes.
>Make the large loop::Lay a broad loop in the rope, keeping both standing sections visible.
>Tuck it beneath the standing sections::Move the loop under the two sections leading away from it, preserving the original crossing.
>Fold both sides inward::Fold one half across the centre, then fold the opposite half across. You should now see two side openings and one central opening.
>Draw out the eyes::Take one side of the central loop up through its neighbouring side opening; repeat on the other side.
>Balance and dress::Pull the two new eyes evenly, work out trapped slack and compare the crossing pattern before snugging the knot.

@bottle-sling|Bottle Sling|bottle-sling-knot|Hitch|3|Everyday,Decorative|Jug sling
=Arrange an adjustable collar and carrying loops around a bottle neck.
+Soft cord and an empty plastic bottle with a pronounced neck lip.
!Practice near a tabletop with an empty plastic bottle. Not for glass, heavy containers or suspending anything above a person.
?The central collar seats beneath the bottle's lip, while the larger loops form the handles.
xUsing a straight-sided container without a retaining lip can let the sling slide off.
>Lay out a broad loop::Arrange a large rope loop on the table and make a small inward bight along one side.
>Fold in the opposite side::Bring a second bight downward from the opposite side of the large loop.
>Interlock the bights::Tuck that second bight under the first, creating a new small opening.
>Bring the handles through::Pass the far end of the large loop beneath the new opening, then pass the near end beneath the original bight, following the demonstration.
>Fit and inspect::Open the centre over the empty bottle neck, snug it beneath the lip and gently bring the handle loops together while keeping the bottle supported.

@crown-sinnet|Crown Sinnet|crown-sinnet-knot|Decorative|2|Decorative|Box braid,Scoubidou,Boondoggle
=Build a square decorative braid from alternating four-strand crowns.
+Four soft coloured strands secured together at one end.
!Decoration only. Do not use as a safety tether or make non-breakaway neckwear.
?Each layer contains four interlocking folds; alternating direction keeps the braid square instead of spiralling.
xAlways crowning in the same direction creates a spiral rather than this box-shaped braid.
>Arrange the four ends::Secure their bases together and spread the working tips toward four sides.
>Start a crown::Fold the first strand over its neighbour, then fold that neighbour over the next. Keep the first fold open.
>Close the fourth fold::Continue around all four strands and pass the last strand through the opening left by the first. Snug all four evenly.
>Reverse the next layer::Make another four-strand crown in the opposite circular direction.
>Continue and finish::Alternate directions, tightening each layer before starting the next. Finish with an appropriate craft binding, such as whipping the ends together.

@square-lashing|Square Lashing|square-lashing-knot|Lashing|2|Camping|Square lashing knot
=Bind two touching poles together at a right angle.
+Two small smooth practice sticks and a long soft cord.
!Practice on a tabletop model. Real structures, platforms and ladders require competent design and inspection, not just a familiar knot.
?Wrapping turns bind both sticks; perpendicular frapping turns squeeze those wraps together between the sticks.
xContinuing around the poles instead of frapping between them misses the tightening stage.
>Anchor the cord::Cross the sticks at a right angle and tie a Clove Hitch around one near the crossing. Twist the short tail alongside the working part.
>Wrap the crossing::Lead the cord over and under the two sticks in a square path around their meeting point.
>Add close wraps::Make three or four circuits, adding turns inside the previous ones on one stick and outside them on the other.
>Frap between the sticks::Take three or four turns around the wrapping bundle between the sticks, pulling them snug to compress the wraps.
>Secure the finish::Finish with two or three tight half hitches as shown. Inspect the model from all sides before trying a light hand movement.
'''
REPLACE={'adjustable-grip':'double-alpine-butterfly-loop','double-dragon':'spanish-bowline','fg':'bottle-sling','halter-hitch':'crown-sinnet','wall-and-crown':'square-lashing'}
# Corrections below align the companion reader with the specific credited method,
# not merely another similarly named knot. No publisher prose is reproduced.
EDITS={
'alpine-butterfly-bend':{
 'check':'Both standing parts leave on opposite sides, while the two independent tails emerge from the butterfly centre.',
 'steps':[
  'Join the tips temporarily::Hold the two rope tips together with a small piece of removable tape. This is only a tying aid, not part of the finished bend.',
  'Wrap around your hand::Wind the joined ropes around your open hand, placing the temporary join near your fingertips. Continue around for another loose turn.',
  'Lift the joined section::Fold the joined section back over the other rope sections, keeping the temporary join together.',
  'Tuck beneath the other sections::Bring the joined section up underneath the other wraps, following the butterfly path shown in the demonstration.',
  'Remove and inspect::Slide the knot off your hand, dress it gently and remove the temporary tape. Confirm two separate, generous tails remain before setting.']},
'anchor-hitch':{
 'check':'The first locking tuck runs under the slack second turn around the ring; a further half hitch surrounds the standing part.',
 'steps':[
  'Make two turns::Pass the working end through the ring twice. Leave the second turn slack enough for the tail to pass underneath it.',
  'Cross the standing part::Bring the tail over the standing part to begin the locking hitch.',
  'Tuck under the slack turn::Feed the tail under the original loose second turn around the ring, then draw this tuck snug.',
  'Add the finishing half hitch::Take the tail around the standing part again and through its own opening in the same rotational direction.',
  'Dress the attachment::Snug the turns against the ring, check the locking tuck and leave a generous visible tail for practice.']},
'ashley-bend':{
 'check':'As the knot sets, each tail rotates toward the opposite rope\'s standing part. Trace the central crossings, not just the outline.',
 'steps':[
  'Make two open folds::Form a U-shaped bight near each rope end, keeping the tails long.',
  'Interlock the bights::Place the two folds through one another, with their curved ends overlapping.',
  'Cross the first tail::Take one tail over its own standing part, under both ropes, and up through the centre beside its own standing section.',
  'Repeat with the other tail::Follow the matching route with the other colour: over itself, under the pair, then up through the centre.',
  'Dress and allow rotation::Take up slack slowly. Each tail turns toward the other rope\'s standing part as the knot forms; check this arrangement before tightening.']},
'boom-hitch':{
 'check':'Four correctly ordered turns surround the spar, the fourth tucks beneath the second, and the tail has an overhand stopper.',
 'steps':[
  'Wrap toward one side::Take the first turn around the spar, leading it to one side of the standing part.',
  'Wrap toward the other side::Make the second turn around the spar toward the opposite side.',
  'Cover the first turn::Bring the third turn back toward the original side, laying it over the first turn.',
  'Tuck the fourth turn::Take the fourth turn outside the second turn and then beneath that second turn, preserving the crossing order.',
  'Add a stopper and dress::Tie an overhand stopper in the tail. Work slack out gradually without rearranging the turns, then inspect the front and back.']},
'butchers-knot':{
 'check':'A sliding overhand grips around the bundle; a loop from the long section is tightened over the short end to secure it.',
 'steps':[
  'Go around the bundle::Wrap the twine around the practice bundle, leaving a short end and a longer working section.',
  'Tie around the long section::Use the short end to make one overhand knot around the long standing section. Keep its orientation matched to the demonstration.',
  'Draw the binding snug::Pull the long section through the overhand knot to remove slack around the bundle without crushing it.',
  'Place the locking loop::Make a small loop in the long section around your fingers, then slide that loop over the short end.',
  'Set the finish::Draw both ends to snug the locking turn against the initial knot. Check the binding before carefully trimming excess twine.']},
'buntline-hitch':{
 'steps':[
  'Go around the object::Pass the tail around the post or through the ring and return beside the standing part.',
  'Circle the standing part::Make a complete turn around the standing section, leaving the wrapping loose. Do not yet tighten it as a separate half hitch.',
  'Return beside the object::Lead the tail through the opening next to the post, following the inverted clove-hitch path in the demonstration.',
  'Complete the half hitch::Take the tail around and tuck it beneath the final crossing to finish the small clove-hitch structure around the standing part.',
  'Slide and set::Draw the hitch against the object and snug all turns. Keep a generous tail and remember this hitch can jam tightly.']},
'carrick-bend':{
 'steps':[
  'Lay the first loop::Make an open loop in the first rope with its tail underneath its standing part. Keep this flat throughout the weaving stage.',
  'Place the second rope::Bring the second tail underneath the first loop, leaving enough length to weave back across it.',
  'Go around the two ends::Carry the second tail over the first standing part and under the first tail, retaining the flat layout.',
  'Weave across the loop::Take the second tail over the first loop\'s edge, under its own second-rope section at the centre, then over the opposite edge.',
  'Trace and dress::Check the alternating over-under crossings before pulling the standing parts. Let the flat pattern settle naturally, maintaining long tails.']},
'davy':{
 'check':'The tag is trapped against the eye beneath the knot, not displaced toward the middle of the eye.',
 'steps':[
  'Thread the eye::Pass the tag through the eye and bring it beside the long line.',
  'Make the half hitch::Take the tag around the long line and back through the eye-side loop. Keep that small half hitch open.',
  'Continue around the loop::Carry the tag around and back through the same loop a second time, as shown. Do not substitute a loose figure eight.',
  'Position the tag::Keep the tag close against the eye, beneath the crossing that will trap it.',
  'Set with your fingers::Draw the knot to the eye carefully, using your fingers rather than your teeth to hold the tag. Inspect and test before trimming.']},
'double-davy':{
 'check':'Three passes complete the loop, with the tag captured against the eye underneath the finished knot.',
 'steps':[
  'Thread and half hitch::Pass the tag through the eye, around the long line and back through the first loop to form a loose half hitch.',
  'Make the second pass::Continue around and back through the loop, beginning the ordinary Davy pattern.',
  'Add the extra pass::Take the tag around the long line once more and through the loop a third time.',
  'Keep the tag beside the eye::Arrange the final tuck so the tag will be enclosed beneath the knot against the eye.',
  'Dress and test::Use your fingers to control the tag while drawing the knot snug. Inspect its eye-side position and test before trimming.']},
'egg-loop':{
 'aliases':['Bumper knot'],
 'steps':[
  'Lay a short section along the hook::Pass one leader end through the eye and hold the short section against the shank.',
  'Make the first wrap stack::Use the long section to wrap around the shank and short section about fifteen times for this demonstrated method.',
  'Return the long end through the eye::Keep the wraps pinched while passing the long end through the eye again, forming a large loop beside the hook.',
  'Wrap with the new loop::Use that loop to make about seven more wraps around the shank and line, keeping all coils controlled and uncrossed.',
  'Draw closed and inspect::Pull the long end to close the loop and set the wraps. Push a little line back through the eye to open and check the bait loop.']},
'figure-eight-directional':{
 'steps':[
  'Identify the standing side::Lay the rope straight and mark which section is intended to receive the pull along the rope.',
  'Make a crossing loop::Form a loop in the middle of the rope, keeping its crossing oriented as in the demonstration.',
  'Carry the loop behind::Take the whole loop behind the marked standing section, preserving the original crossing.',
  'Continue around and through::Bring the loop around that section and through the opening beside the other rope section, making the directional figure-eight path.',
  'Dress and check the direction::Align the eye with the intended loaded section and snug the strands. Use only a light hand test in that direction, not a suspended load.']},
'heaving-line':{
 'check':'The early turns surround two strands; the later turns surround three, with the tail retained through the end loop.',
 'steps':[
  'Fold the rope::Make a long bight near the end and position it so the working tail lies inside the folded arrangement.',
  'Start around two strands::Use the working tail to make the initial wraps around the two appropriate strands shown in the demonstration.',
  'Enclose the third strand::Continue winding around all three parallel strands, adding close coils as you use up the working tail.',
  'Pass through the end loop::Feed the remaining tail through the loop left at the end of the coil bundle.',
  'Dress the soft bundle::Work the coils together and close the retaining loop around the tail. Keep the knot entirely soft and unweighted.']},
'lightermans-hitch':{
 'aliases':['Tugboat hitch'],
 'steps':[
  'Make the round turn::Pass the working rope around the bollard twice, keeping both initial turns at its base.',
  'Make a folded section::Take a bight in the working section, leaving the standing part leading away from the bollard.',
  'Pass under and over::Carry the bight under the standing part, then open it over the top of the bollard.',
  'Add a turn and repeat::Make an additional turn around the bollard, then place another bight under the standing part and over the bollard as shown.',
  'Continue and dress::Add further alternating turns as appropriate to the practice example. Snug every turn and keep hands clear of any loaded section.']},
'marlinspike-hitch':{
 'check':'The bight comes from the standing part, so a light pull on that part closes the hitch around the toggle.',
 'steps':[
  'Identify the standing part::Lay the rope on the table and mark the side that will be pulled. The bight must come from this side.',
  'Make a crossing loop::Form a small loop in the rope and keep its opening loose.',
  'Pull a standing-part bight through::Fold a section of the marked standing part and draw that fold through the crossing loop.',
  'Insert the toggle::Pass the smooth toggle through the emerging bight, centring it so both ends extend well beyond the rope.',
  'Seat and inspect::Apply only light hand tension to the standing part and verify that it tightens the grip. Unload completely before removing the toggle.']},
'non-slip-mono':{
 'check':'The tag returns from the wrap stack through the overhand opening toward the lure, and the finished loop remains fixed.',
 'steps':[
  'Make a loose overhand::Tie an open overhand knot near the line end, leaving a generous tag.',
  'Go through the eye and back::Pass the tag through the lure eye and back through the loose overhand opening toward the standing line.',
  'Make the wrap stack::Wrap the tag around the standing line about five times for the illustrated method. Actual line diameter may require a different count.',
  'Return toward the lure::Bring the tag back from the wrap stack through the overhand opening toward the eye, following the displayed crossing.',
  'Set the eye size::Adjust the lure loop while everything is loose. Moisten, dress and tighten the knot, then inspect and test before trimming.']},
'stevedore':{
 'steps':[
  'Fold a bight::Make a U-shaped fold near the rope end, leaving a long working tail.',
  'Cross the standing part::Take the tail across the standing section to begin the winding.',
  'Make two full turns::Continue around the standing section for two complete turns, keeping the turns beside one another.',
  'Return through the bight::Pass the working tip down through the original folded opening after the two turns.',
  'Dress the stopper::Snug the winding section, then pull the standing part to grip the tail. Inspect the compact stopper and leave the tail visible.']}
}
new={k['id']:k for k in parse_catalog(NEW,'review replacements')}
for name in ['catalog-a.txt','catalog-b.txt','catalog-c.txt','catalog-d.txt']:
    path=SRC/name; records=parse_catalog(path.read_text(),name); result=[]
    for old in records:
        k=new[REPLACE[old['id']]] if old['id'] in REPLACE else old
        if k['id'] in EDITS:k.update(EDITS[k['id']])
        result.append(k)
    path.write_text(serialize(result),encoding='utf-8')
print('Initial editorial review applied. The four source catalogs now contain the publication-ready records.')
