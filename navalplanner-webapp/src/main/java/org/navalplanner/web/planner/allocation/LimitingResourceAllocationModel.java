/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.planner.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.navalplanner.business.orders.daos.IHoursGroupDAO;
import org.navalplanner.business.orders.entities.AggregatedHoursGroup;
import org.navalplanner.business.orders.entities.HoursGroup;
import org.navalplanner.business.orders.entities.TaskSource;
import org.navalplanner.business.planner.daos.ITaskElementDAO;
import org.navalplanner.business.planner.daos.ITaskSourceDAO;
import org.navalplanner.business.planner.entities.LimitingResourceQueueElement;
import org.navalplanner.business.planner.entities.ResourceAllocation;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.resources.daos.ICriterionDAO;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionType;
import org.navalplanner.business.resources.entities.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides logical operations for limiting resource assignations in @{Task}
 *
 * @author Diego Pino García <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class LimitingResourceAllocationModel implements ILimitingResourceAllocationModel {

    @Autowired
    private IHoursGroupDAO hoursGroupDAO;

    @Autowired
    private ITaskSourceDAO taskSourceDAO;

    @Autowired
    private ICriterionDAO criterionDAO;

    @Autowired
    private ITaskElementDAO taskDAO;

    @Autowired
    private IResourceDAO resourceDAO;

    private Task task;

    private List<LimitingAllocationRow> limitingAllocationRows = new ArrayList<LimitingAllocationRow>();

    @Override
    public void init(Task task) {
        this.task = task;
        limitingAllocationRows = LimitingAllocationRow.toRows(task);
    }

    @Override
    @Transactional(readOnly=true)
    public Integer getOrderHours() {
        if (task == null) {
            return 0;
        }
        return AggregatedHoursGroup.sum(getHoursAggregatedByCriteria());
    }

    @Override
    @Transactional(readOnly = true)
    public void addGeneric(Set<Criterion> criteria,
            Collection<? extends Resource> resources) {
        if (resources.size() >= 1) {
            reattachResources(resources);
            addGenericResourceAllocation(criteria, resources);
        }
    }

    private void reattachResources(Collection<? extends Resource> resources) {
        for (Resource each: resources) {
            resourceDAO.reattach(each);
        }
    }

    private void addGenericResourceAllocation(Set<Criterion> criteria,
            Collection<? extends Resource> resources) {
        if (isNew(criteria, resources)) {
            limitingAllocationRows.clear();
            LimitingAllocationRow allocationRow = LimitingAllocationRow.create(
                    criteria, resources, task, LimitingAllocationRow.DEFAULT_PRIORITY);
            limitingAllocationRows.add(allocationRow);
        }
    }

    private boolean isNew(Set<Criterion> criteria, Collection<? extends Resource> resources) {
        LimitingAllocationRow allocationRow = getLimitingAllocationRow();

        if (allocationRow == null || allocationRow.isSpecific()) {
            return true;
        }

        Set<Long> allocatedResourcesIds = allocationRow.getResourcesIds();
        for (Resource each: resources) {
            if (!allocatedResourcesIds.contains(each.getId())) {
                return true;
            }
        }

        Set<Long> allocatedCriteriaIds = allocationRow.getCriteriaIds();
        for (Criterion each: criteria) {
            if (!allocatedCriteriaIds.contains(each.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void addSpecific(Collection<? extends Resource> resources) {
        if (resources.size() >= 1) {
            Resource resource = getFirstChild(resources);
            resourceDAO.reattach(resource);
            addSpecificResourceAllocation(resource);
        }
    }

    public Resource getFirstChild(Collection<? extends Resource> collection) {
        return collection.iterator().next();
    }

    private void addSpecificResourceAllocation(Resource resource) {
        if (isNew(resource)) {
            limitingAllocationRows.clear();
            LimitingAllocationRow allocationRow = LimitingAllocationRow.create(
                    resource, task, LimitingAllocationRow.DEFAULT_PRIORITY);
            limitingAllocationRows.add(allocationRow);
        }
    }

    private boolean isNew(Resource resource) {
        LimitingAllocationRow allocationRow = getLimitingAllocationRow();

        if (allocationRow == null || allocationRow.isGeneric()) {
            return true;
        }

        final Resource taskResource = getAssociatedResource();
        if (taskResource != null) {
            return (!resource.getId().equals(taskResource.getId()));
        }
        return true;
    }

    private Resource getAssociatedResource() {
        ResourceAllocation<?> resourceAllocation = getAssociatedResourceAllocation();
        if (resourceAllocation != null) {
            List<Resource> resources = resourceAllocation.getAssociatedResources();
            if (resources != null && resources.size() >= 1) {
                return (Resource) resources.iterator().next();
            }
        }
        return null;
    }

    private ResourceAllocation<?> getAssociatedResourceAllocation() {
        LimitingAllocationRow allocationRow = getLimitingAllocationRow();
        if (allocationRow != null) {
            return allocationRow.getResourceAllocation();
        }
        return null;
    }

    private LimitingAllocationRow getLimitingAllocationRow() {
        if (limitingAllocationRows.size() >= 1) {
            return limitingAllocationRows.get(0);
        }
        return null;
    }

    @Override
    public List<LimitingAllocationRow> getResourceAllocationRows() {
        return Collections.unmodifiableList(limitingAllocationRows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AggregatedHoursGroup> getHoursAggregatedByCriteria() {
        reattachTaskSource();
        List<AggregatedHoursGroup> result = task.getTaskSource()
                .getAggregatedByCriterions();
        ensuringAccesedPropertiesAreLoaded(result);
        return result;
    }

    private void ensuringAccesedPropertiesAreLoaded(
            List<AggregatedHoursGroup> result) {
        for (AggregatedHoursGroup each : result) {
            each.getCriterionsJoinedByComma();
            each.getHours();
        }
    }

    /**
     * Re-attach {@link TaskSource}
     */
    private void reattachTaskSource() {
        TaskSource taskSource = task.getTaskSource();
        taskSourceDAO.reattach(taskSource);
        Set<HoursGroup> hoursGroups = taskSource.getHoursGroups();
        for (HoursGroup hoursGroup : hoursGroups) {
            reattachHoursGroup(hoursGroup);
        }
    }

    private void reattachHoursGroup(HoursGroup hoursGroup) {
        hoursGroupDAO.reattachUnmodifiedEntity(hoursGroup);
        hoursGroup.getPercentage();
        reattachCriteria(hoursGroup.getValidCriterions());
    }

    private void reattachCriteria(Set<Criterion> criterions) {
        for (Criterion criterion : criterions) {
            reattachCriterion(criterion);
        }
    }

    private void reattachCriterion(Criterion criterion) {
        criterionDAO.reattachUnmodifiedEntity(criterion);
        criterion.getName();
        reattachCriterionType(criterion.getType());
    }

    private void reattachCriterionType(CriterionType criterionType) {
        criterionType.getName();
    }

    @Override
    @Transactional(readOnly=true)
    public void confirmSave() {
        taskDAO.reattach(task);
        ResourceAllocation<?> resourceAllocation = getAssociatedResourceAllocation();
        if (resourceAllocation != null && resourceAllocation.isNewObject()) {
            task.removeAllResourceAllocations();
            addResourceAllocation(task, resourceAllocation);
        }
        taskDAO.save(task);
    }

    private void addResourceAllocation(Task task, ResourceAllocation<?> resourceAllocation) {
        LimitingResourceQueueElement element = LimitingResourceQueueElement.create();
        element.setEarlierStartDateBecauseOfGantt(task.getStartDate());
        resourceAllocation.setLimitingResourceQueueElement(element);
        task.addResourceAllocation(resourceAllocation, false);
    }

}