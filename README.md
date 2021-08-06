
# DMX Little Helpers

A plugin development helper comprised of the following functionalities. It should be named `dmx-utils` but the code does not do justice to the concept of a utility in terms of its robustness and the API does not in terms of its clarity.

To see if it might be of use to you find the API described in [HelperService.java](https://git.dmx.systems/dmx-plugins/dmx-littlehelpers/-/blob/master/src/main/java/systems/dmx/littlehelpers/HelperService.java).

Time Search API (HTTP):

- `/timeindex/{time_value}/{since}/{to}` (builds Index for visually supporting time-queries)
- `/by_time/{time_value}/{since}/{to}` (queries `Note`, `File`, `Folder`, `Person`, `Organization` and `Bookmark` topics)

Parameter `time_value` can be either `created` or `modified`.

Results of this API are each enriched about an icon path and their `created` and `modified` timestamps.

Topic search API (delivering a list of `SearchResult`):

- `/suggest/topics/{input}` (queries `Note Title`, `Topicmaps Name` and `Username`)
- `/suggest/topics/{input}/{typeUri}`
 
`Search Result` of this endpoint _are_ each enriched about their `creator (username)` and `workspace` info.

Topicmaps utilities:

- `getTopicmapsByMaptype`

Datetime utilities:

- `setDateTopic(ChildTopicsModel cm, Date date, String assocTypeUri)`

Topic Type utilities:

- `getTopicTypesConfiguredForCreateMenu`

Workspace utilities:

- `getFirstWorkspaceByName`

Sorting utilities:

- `getTopicListSortedByCreationTime`
- `getTopicListSortedByModificationTime`
- `sortCompareToBySimpleValue`
- `sortCompareToByChildTypeValue`

Licensing
---------

DMX Little Helpers is available freely under the GNU Affero General Public License, version 3 or later (see [License](https://git.dmx.systems/dmx-plugins/dmx-littlehelpers/-/blob/master/LICENSE)).

Release History
---------------

**0.6.1** -- Aug 06, 2021

Maintenance release fixing the version number in `pom.xml`

**0.6.0** -- Aug 05, 2021

* Add date time topic helper method (builder)
* Add '/open-in-map/{topicmapId}/{topicId}' endpoint
* Adapted to be compatible with DMX 5.2
* Fixes outdated URI (DM4) in SearchResult model class 
* Helper method to store java Date objects as topics
* Helpers to fetch types, workspaces and topicmaps by type or name

**0.5.0** -- Jan 03, 2021

* Adapted to be compatible with DMX 5.1
* Released under the AGPL-3.0 license
* Added "Open in Topicmap" endpoint

**0.4.0** -- Feb 02, 2018

* Adapted to breaking changes in DM 4.8.7

**0.3.0** -- Nov 15, 2016

* Basically working

Copyright
---------
Copyright (C) 2015-2018 Malte Reißig

Copyright (C) 2019-2021 DMX Systems


