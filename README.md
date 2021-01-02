
# DMX Little Helpers

A plugin development helper comprised of the following functionalities. May become dmx-utils if the code improves in terms of robustness and the API in terms of clarity.

Time Search API:

- `/timeindex/{time_value}/{since}/{to}` (builds Index for visually supporting time-queries)
- `/by_time/{time_value}/{since}/{to}` (queries `Note`, `File`, `Folder`, `Person`, `Organization` and `Bookmark` topics)

Parameter `time_value` can be either `created` or `modified`.

Results of this API are each enriched about an icon path and their `created` and `modified` timestamps.

Topic query delivering a list of `SearchResult` (custom ViewModel):

- `/suggest/topics/{input}` (queries `Note Title`, `Topicmaps Name` and `Username`)
- `/suggest/topics/{input}/{typeUri}`

`Search Result` of this endpoint are each enriched about their `username` and `workspace` info.

Sorting utilities:

- `getTopicListSortedByCreationTime`
- `getTopicListSortedByModificationTime`
- `sortCompareToBySimpleValue`
- `sortCompareToByChildTypeValue`

Licensing
---------

DMX Little Helpers software is available freely under the GNU Affero General Public License, version 3.

All third party components incorporated into the DMX Little Helpers Software are licensed under the original license provided by the owner of the applicable component.

Release History
---------------

**0.5.0** -- Upcoming

* Adapted to be compatible with DMX 5.1
* Released under the AGPL-3.0 license
* Added "Open in Topicmap" endpoint
* Code organization

**0.4.0** -- Feb 02, 2018

* Adapted to breaking changes in DM 4.8.7

**0.3.0** -- Nov 15, 2016

* Basically working

Copyright
---------
Copyright (C) 2015, 16, 2018 Malte Reißig
Copyright (C) 2019-2021 DMX Systems


